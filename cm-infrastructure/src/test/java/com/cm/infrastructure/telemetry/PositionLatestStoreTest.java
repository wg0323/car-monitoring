package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 最新位置：版本比较在原子脚本里完成，乱序与迟到事件不会覆盖更新的位置。
 * 默认不跑，设置 CM_P3_REDIS_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_REDIS_TEST", matches = "true")
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.data.redis.host=${CM_P3_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${CM_P3_REDIS_PORT:6379}",
        "spring.data.redis.password=${CM_P3_REDIS_PASSWORD:}",
        "cm.telemetry.latest.key-prefix=cm:test:position:",
        "cm.telemetry.latest.ttl-seconds=60"
})
@Import(PositionLatestStore.class)
class PositionLatestStoreTest {

    private static final String SIM_NO = "13800138000";

    private static final String KEY = "cm:test:position:" + SIM_NO;

    private static final Instant DEVICE_TIME = Instant.parse("2026-11-15T04:00:00Z");

    private static final Instant INGEST_TIME = Instant.parse("2026-11-15T04:00:02Z");

    @Autowired
    private PositionLatestStore latestStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("首次写入成功并可读回")
    void should_write_first_position() {
        assertTrue(latestStore.updateIfNewer(event("E1", DEVICE_TIME, INGEST_TIME)));
        assertTrue(latestStore.find(SIM_NO).isPresent());
    }

    @Test
    @DisplayName("设备时间更新的事件覆盖旧值")
    void should_overwrite_with_newer_device_time() {
        assertTrue(latestStore.updateIfNewer(event("E1", DEVICE_TIME, INGEST_TIME)));
        assertTrue(latestStore.updateIfNewer(event("E2", DEVICE_TIME.plusSeconds(30), INGEST_TIME.plusSeconds(30))));
        assertTrue(latestStore.find(SIM_NO).orElse("").contains("\"eventId\":\"E2\""));
    }

    @Test
    @DisplayName("迟到事件不会覆盖更新的位置")
    void should_reject_older_device_time() {
        assertTrue(latestStore.updateIfNewer(event("E2", DEVICE_TIME.plusSeconds(30), INGEST_TIME.plusSeconds(30))));
        assertFalse(latestStore.updateIfNewer(event("E1", DEVICE_TIME, INGEST_TIME)));
        assertTrue(latestStore.find(SIM_NO).orElse("").contains("\"eventId\":\"E2\""));
    }

    @Test
    @DisplayName("版本完全相同时不重复写入")
    void should_reject_same_version() {
        assertTrue(latestStore.updateIfNewer(event("E1", DEVICE_TIME, INGEST_TIME)));
        assertFalse(latestStore.updateIfNewer(event("E1", DEVICE_TIME, INGEST_TIME)));
    }

    @Test
    @DisplayName("设备时间相同则以接入时间兜底排序，版本串为固定宽度毫秒")
    void should_compare_ingest_time_then_event_id() {
        assertTrue(latestStore.updateIfNewer(event("E1", DEVICE_TIME, INGEST_TIME)));
        assertTrue(latestStore.updateIfNewer(event("E2", DEVICE_TIME, INGEST_TIME.plusSeconds(1))));
        assertFalse(latestStore.updateIfNewer(event("E3", DEVICE_TIME, INGEST_TIME)));
        String version = PositionLatestStore.version(event("E2", DEVICE_TIME, INGEST_TIME.plusSeconds(1)));
        String expectedPrefix = String.format("%013d|%013d|", DEVICE_TIME.toEpochMilli(),
                INGEST_TIME.plusSeconds(1).toEpochMilli());
        assertTrue(version.startsWith(expectedPrefix), "版本串必须是固定宽度毫秒加事件 ID");
    }

    private PositionEvent event(String eventId, Instant deviceTime, Instant ingestTime) {
        PositionSample sample = new PositionSample(0, 1, 30000000, 120000000, 0, 60, 90, 100L, 0, 0, 0, 0);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, eventId, PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, SIM_NO, 1, deviceTime.toString(), ingestTime.toString(),
                deviceTime.toString(), "NORMAL", "gw-1", eventId, 0, sample, "abcdef0123456789");
    }
}
