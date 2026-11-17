package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Redis 最新位置。版本串由 (有效设备时间, 接入时间, 事件 ID) 拼成固定宽度文本，
 * 在 Lua 里比较后再写入，乱序与迟到事件不会覆盖更新的位置。
 */
@Component
@RequiredArgsConstructor
public class PositionLatestStore {

    /** 返回 1 表示写入，0 表示已有同版本或更新版本。 */
    private static final DefaultRedisScript<Long> UPDATE_IF_NEWER = new DefaultRedisScript<>(
            "local current = redis.call('HGET', KEYS[1], 'ver') "
                    + "if current and current >= ARGV[1] then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'ver', ARGV[1], 'data', ARGV[2]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[3]) "
                    + "return 1",
            Long.class);

    private static final String VERSION_FORMAT = "%013d|%013d|%s";

    private static final String FIELD_VERSION = "ver";

    private static final String FIELD_DATA = "data";

    private final StringRedisTemplate redisTemplate;

    private final TelemetryProperties properties;

    public boolean updateIfNewer(PositionEvent event) {
        Long updated = redisTemplate.execute(UPDATE_IF_NEWER, List.of(key(event.simNo())),
                version(event), TelemetryJson.write(event), ttlMillis());
        return updated != null && updated > 0;
    }

    public Optional<String> find(String simNo) {
        Object data = redisTemplate.opsForHash().get(key(simNo), FIELD_DATA);
        return data == null ? Optional.empty() : Optional.of(data.toString());
    }

    /**
     * 与 PositionVersion 的比较口径一致：毫秒固定 13 位，字典序等价于数值序，最后以事件 ID 兜底。
     */
    public static String version(PositionEvent event) {
        long effective = Instant.parse(event.effectiveDeviceTime()).toEpochMilli();
        long ingest = Instant.parse(event.ingestTime()).toEpochMilli();
        return String.format(VERSION_FORMAT, effective, ingest, event.eventId());
    }

    private String key(String simNo) {
        return properties.getLatest().getKeyPrefix() + simNo;
    }

    private String ttlMillis() {
        return String.valueOf(properties.getLatest().getTtlSeconds() * 1000L);
    }
}
