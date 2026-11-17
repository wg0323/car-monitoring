package com.cm.infrastructure.command;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 终端路由的 Redis 读写。路由只在鉴权成功后建立，删除用比较后执行，
 * 防止旧连接的延迟关闭事件删掉新连接的路由（方案 10.4）。
 */
@Component
@RequiredArgsConstructor
public class TerminalRouteRegistry {

    private static final String KEY_PREFIX = "cm:route:";

    /** 比较后删除：值仍是期望的连接代号才删。 */
    private static final DefaultRedisScript<Long> REMOVE_IF_MATCH = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    private final CmCommandProperties properties;

    public void register(String simNo, TerminalRoute route) {
        redisTemplate.opsForValue().set(key(simNo), route.format(),
                Duration.ofSeconds(properties.getRouteTtlSeconds()));
    }

    /**
     * 读取路由；值缺失或格式非法都返回空，不向下行链路传递不可信网关。
     */
    public Optional<TerminalRoute> find(String simNo) {
        String value = redisTemplate.opsForValue().get(key(simNo));
        TerminalRoute route = TerminalRoute.parse(value);
        return Optional.ofNullable(route);
    }

    /**
     * 仅当路由仍指向 expected 时才删除。
     */
    public boolean removeIfMatch(String simNo, TerminalRoute expected) {
        Long removed = redisTemplate.execute(REMOVE_IF_MATCH, List.of(key(simNo)), expected.format());
        return removed != null && removed > 0;
    }

    private String key(String simNo) {
        return KEY_PREFIX + simNo;
    }
}
