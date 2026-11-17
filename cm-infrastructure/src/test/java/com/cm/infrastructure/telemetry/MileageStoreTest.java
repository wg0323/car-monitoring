package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 里程累计：基线、正常增量、累计值回退、跳变超限、CAS 冲突。
 * 默认不跑，设置 CM_P3_REDIS_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_REDIS_TEST", matches = "true")
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.data.redis.host=${CM_P3_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${CM_P3_REDIS_PORT:6379}",
        "spring.data.redis.password=${CM_P3_REDIS_PASSWORD:}",
        "cm.telemetry.time.zone=Asia/Shanghai",
        "cm.telemetry.mileage.base-key-prefix=cm:test:mileage:base:",
        "cm.telemetry.mileage.day-key-prefix=cm:test:mileage:day:",
        "cm.telemetry.mileage.max-delta-kilometers=100",
        "cm.telemetry.mileage.retention-days=7"
})
@Import({MileageStore.class, TelemetryMetrics.class})
class MileageStoreTest {

    private static final String SIM_NO = "P3-MILEAGE-SIM";

    private static final String BASE_KEY = "cm:test:mileage:base:" + SIM_NO;

    private static final String DAY_KEY = "cm:test:mileage:day:" + SIM_NO + ":20261115";

    private static final String DEVICE_TIME = "2026-11-15T02:00:00Z";

    private static final VehicleBinding BINDING = new VehicleBinding(9001L, 7L, "测A0001", 2, 11L);

    @Autowired
    private MileageStore mileageStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("首次样本只建基线，当日累计仍为 0")
    void should_build_baseline_only() {
        assertFalse(mileageStore.accumulate(event(1000L), BINDING));

        assertEquals("1000", redisTemplate.opsForHash().get(BASE_KEY, "raw"));
        assertEquals("0", decimalOf("total"));
        assertEquals(0, new BigDecimal(decimalOf("first")).compareTo(new BigDecimal("100")));
    }

    @Test
    @DisplayName("第二个样本按 0.1 km 单位换算累加")
    void should_accumulate_delta_in_kilometers() {
        mileageStore.accumulate(event(1000L), BINDING);
        assertTrue(mileageStore.accumulate(event(1050L), BINDING));

        assertEquals(0, new BigDecimal(decimalOf("total")).compareTo(new BigDecimal("5")));
        assertEquals(0, new BigDecimal(decimalOf("first")).compareTo(new BigDecimal("100")));
        assertEquals(0, new BigDecimal(decimalOf("last")).compareTo(new BigDecimal("105")));
        assertEquals("测A0001", redisTemplate.opsForHash().get(DAY_KEY, "plateNo"));
    }

    @Test
    @DisplayName("累计值回退时不计入，但基线要重置")
    void should_reset_baseline_without_counting() {
        mileageStore.accumulate(event(1000L), BINDING);
        mileageStore.accumulate(event(1050L), BINDING);
        assertFalse(mileageStore.accumulate(event(10L), BINDING));

        assertEquals(0, new BigDecimal(decimalOf("total")).compareTo(new BigDecimal("5")));
        assertEquals("10", redisTemplate.opsForHash().get(BASE_KEY, "raw"));
    }

    @Test
    @DisplayName("单点跳变超过阈值时不计入")
    void should_discard_oversized_delta() {
        mileageStore.accumulate(event(1000L), BINDING);

        assertFalse(mileageStore.accumulate(event(1000L + 1001L), BINDING));
        assertEquals("0", decimalOf("total"));
    }

    @Test
    @DisplayName("基线在读取后被其他消费者推进时本次跳过，不双算")
    void should_skip_when_baseline_moved() {
        mileageStore.accumulate(event(1000L), BINDING);
        redisTemplate.opsForHash().put(BASE_KEY, "raw", "2000");

        assertFalse(mileageStore.accumulateFrom(event(1050L), BINDING, "1000"));
        assertEquals("0", decimalOf("total"));
        assertEquals("2000", redisTemplate.opsForHash().get(BASE_KEY, "raw"));
    }

    @Test
    @DisplayName("重复投递同一帧不会双算")
    void should_not_double_count_duplicate_event() {
        mileageStore.accumulate(event(1000L), BINDING);
        assertTrue(mileageStore.accumulate(event(1050L), BINDING));
        assertFalse(mileageStore.accumulate(event(1050L), BINDING));

        assertEquals(0, new BigDecimal(decimalOf("total")).compareTo(new BigDecimal("5")));
    }

    @Test
    @DisplayName("跨天写入不同的当日记账键")
    void should_use_separate_day_key() {
        mileageStore.accumulate(event(1000L), BINDING);
        mileageStore.accumulate(event(1050L), BINDING);
        mileageStore.accumulate(new PositionEvent(PositionEvent.SCHEMA_VERSION, "E2",
                PositionEvent.EVENT_TYPE, PositionEvent.BRANCH_STANDARD, SIM_NO, 2,
                "2026-11-16T01:00:00Z", "2026-11-16T01:00:01Z", "2026-11-16T01:00:00Z", "NORMAL",
                "gw-test", "trace-2", 0, sample(1100L), "digest"), BINDING);

        Set<String> keys = Set.copyOf(mileageStore.dayKeys());
        assertTrue(keys.contains(DAY_KEY));
        assertTrue(keys.contains("cm:test:mileage:day:" + SIM_NO + ":20261116"));
    }

    private String decimalOf(String field) {
        Object value = redisTemplate.opsForHash().get(DAY_KEY, field);
        return value == null ? "0" : value.toString();
    }

    private void cleanUp() {
        redisTemplate.delete(Set.of(BASE_KEY,
                "cm:test:mileage:day:" + SIM_NO + ":20261115",
                "cm:test:mileage:day:" + SIM_NO + ":20261116"));
    }

    private static PositionEvent event(long mileageRaw) {
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, "E1", PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, SIM_NO, 1, DEVICE_TIME, DEVICE_TIME, DEVICE_TIME,
                "NORMAL", "gw-test", "trace-1", 0, sample(mileageRaw), "digest");
    }

    private static PositionSample sample(long mileageRaw) {
        return new PositionSample(0, 1, 30200000, 120000000, 100, 600, 90, mileageRaw, 300, 550, 26, 240);
    }
}
