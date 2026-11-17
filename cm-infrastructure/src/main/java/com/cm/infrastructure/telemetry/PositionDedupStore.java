package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消费端去重门闩。Redis 只做前置拦截：键丢失、被淘汰或宕机恢复后可能放行，
 * 因此它不是严格幂等的事实源，但能挡住终端重报、MQ 重投与死信重放这一类重复。
 * 业务写入失败必须释放键，否则重试会被误判为重复而静默丢数据。
 */
@Component
@RequiredArgsConstructor
public class PositionDedupStore {

    private static final String PRESENT = "1";

    private final StringRedisTemplate redisTemplate;

    private final TelemetryProperties properties;

    /**
     * 抢占事件的处理权。返回 true 表示首次处理，调用方必须在写入失败时调用 release。
     */
    public boolean acquire(String eventId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(eventId), PRESENT,
                Duration.ofSeconds(properties.getDedup().getTtlSeconds()));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放处理权，仅用于本次写入失败后的重试准备。
     */
    public void release(String eventId) {
        redisTemplate.delete(key(eventId));
    }

    private String key(String eventId) {
        return properties.getDedup().getKeyPrefix() + eventId;
    }
}
