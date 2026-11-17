package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 在线租约。本地 Channel 才是连接事实源，Redis 键只做崩溃后的兜底：
 * 心跳比较连接代号后再续期，断线比较后再删除，旧连接既续不到也删不掉新连接的租约。
 */
@Component
@RequiredArgsConstructor
public class OnlineLeaseStore {

    private static final DefaultRedisScript<Long> RENEW_IF_MATCH = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
                    + "else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Long> REMOVE_IF_MATCH = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private static final int SCAN_BATCH = 1000;

    private final StringRedisTemplate redisTemplate;

    private final TelemetryProperties properties;

    public void register(String simNo, String gatewayId, long incarnation) {
        redisTemplate.opsForValue().set(key(simNo), new OnlineLease(gatewayId, incarnation).format(),
                Duration.ofSeconds(properties.getOnline().getLeaseTtlSeconds()));
    }

    /**
     * 仅当无人持有时登记。续租失败可能是租约自然过期，也可能是已被新连接取代，
     * 只有前者可以补登记，后者必须让位，否则旧连接会把在线状态改回已死的 incarnation。
     */
    public boolean registerIfAbsent(String simNo, String gatewayId, long incarnation) {
        Boolean registered = redisTemplate.opsForValue().setIfAbsent(key(simNo),
                new OnlineLease(gatewayId, incarnation).format(),
                Duration.ofSeconds(properties.getOnline().getLeaseTtlSeconds()));
        return Boolean.TRUE.equals(registered);
    }

    public boolean renew(String simNo, String gatewayId, long incarnation) {
        Long renewed = redisTemplate.execute(RENEW_IF_MATCH, List.of(key(simNo)),
                new OnlineLease(gatewayId, incarnation).format(), ttlMillis());
        return renewed != null && renewed > 0;
    }

    public boolean removeIfMatch(String simNo, String gatewayId, long incarnation) {
        Long removed = redisTemplate.execute(REMOVE_IF_MATCH, List.of(key(simNo)),
                new OnlineLease(gatewayId, incarnation).format());
        return removed != null && removed > 0;
    }

    public Optional<OnlineLease> find(String simNo) {
        return OnlineLease.parse(redisTemplate.opsForValue().get(key(simNo)));
    }

    /**
     * 扫描本网关实例名下的在线租约。本机规模用 SCAN 足够，生产应换成本地维护的索引集合。
     */
    public List<String> scanByGateway(String gatewayId) {
        List<String> simNos = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(properties.getOnline().getKeyPrefix() + "*")
                .count(SCAN_BATCH)
                .build();
        String prefix = gatewayId + "|";
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String redisKey = cursor.next();
                String value = redisTemplate.opsForValue().get(redisKey);
                if (value != null && value.startsWith(prefix)) {
                    simNos.add(redisKey.substring(properties.getOnline().getKeyPrefix().length()));
                }
            }
        }
        return simNos;
    }

    private String key(String simNo) {
        return properties.getOnline().getKeyPrefix() + simNo;
    }

    private String ttlMillis() {
        return String.valueOf(properties.getOnline().getLeaseTtlSeconds() * 1000L);
    }
}
