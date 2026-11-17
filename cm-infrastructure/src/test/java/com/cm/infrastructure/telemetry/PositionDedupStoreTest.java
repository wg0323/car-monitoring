package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 去重门闩：同一事件只放行一次，释放后可重试，键随 TTL 过期。
 * 默认不跑，设置 CM_P3_REDIS_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_REDIS_TEST", matches = "true")
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.data.redis.host=${CM_P3_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${CM_P3_REDIS_PORT:6379}",
        "spring.data.redis.password=${CM_P3_REDIS_PASSWORD:}",
        "cm.telemetry.dedup.key-prefix=cm:test:dedup:",
        "cm.telemetry.dedup.ttl-seconds=1"
})
@Import(PositionDedupStore.class)
class PositionDedupStoreTest {

    private static final String EVENT_ID = "jt808-2011:13800138000:0200:1:1737000000:abcdef0123456789:000";

    private static final String KEY = "cm:test:dedup:" + EVENT_ID;

    @Autowired
    private PositionDedupStore dedupStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("同一事件只有第一次抢占成功")
    void should_acquire_only_once() {
        assertTrue(dedupStore.acquire(EVENT_ID));
        assertFalse(dedupStore.acquire(EVENT_ID));
    }

    @Test
    @DisplayName("写入失败释放后允许重试")
    void should_allow_retry_after_release() {
        assertTrue(dedupStore.acquire(EVENT_ID));
        dedupStore.release(EVENT_ID);
        assertTrue(dedupStore.acquire(EVENT_ID));
    }

    @Test
    @DisplayName("键带过期时间，过期后可重新处理")
    void should_expire_after_ttl() throws InterruptedException {
        assertTrue(dedupStore.acquire(EVENT_ID));
        Long ttl = redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS);
        assertTrue(ttl != null && ttl > 0, "去重键必须带 TTL");
        Thread.sleep(1200L);
        assertTrue(dedupStore.acquire(EVENT_ID), "TTL 到期后应重新放行");
    }
}
