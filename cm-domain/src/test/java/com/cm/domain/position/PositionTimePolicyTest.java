package com.cm.domain.position;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间质量四档边界与有效设备时间的取值规则。
 */
class PositionTimePolicyTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Instant INGEST = Instant.parse("2026-11-15T04:00:00Z");

    private static final PositionTimePolicy POLICY = new PositionTimePolicy(
            Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofMinutes(1), ZONE);

    @Test
    @DisplayName("偏差在一分钟内为 NORMAL，有效时间取设备时间")
    void should_mark_normal_within_tolerance() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(0, -1), INGEST);
        assertEquals(TimeQuality.NORMAL, verdict.quality());
        assertEquals(INGEST.minusSeconds(1), verdict.effectiveDeviceTime());
        assertFalse(verdict.quarantined());
    }

    @Test
    @DisplayName("设备时间超前五分钟以上为 FUTURE，有效时间退回接入时间")
    void should_mark_future_when_device_leads() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(6, 0), INGEST);
        assertEquals(TimeQuality.FUTURE, verdict.quality());
        assertEquals(INGEST, verdict.effectiveDeviceTime());
        assertTrue(verdict.quarantined());
    }

    @Test
    @DisplayName("设备时间落后超过一天为 STALE")
    void should_mark_stale_when_device_lags_a_day() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(-1441, 0), INGEST);
        assertEquals(TimeQuality.STALE, verdict.quality());
        assertTrue(verdict.quarantined());
    }

    @Test
    @DisplayName("落后十分钟属于 CLOCK_DRIFT，有效时间保持设备时间")
    void should_keep_device_time_when_clock_drifts_behind() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(-10, 0), INGEST);
        assertEquals(TimeQuality.CLOCK_DRIFT, verdict.quality());
        assertEquals(INGEST.minusSeconds(600), verdict.effectiveDeviceTime());
        assertFalse(verdict.quarantined());
    }

    @Test
    @DisplayName("超前两分钟属于 CLOCK_DRIFT，有效时间被压到接入时间")
    void should_clamp_effective_time_when_clock_drifts_ahead() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(2, 0), INGEST);
        assertEquals(TimeQuality.CLOCK_DRIFT, verdict.quality());
        assertEquals(INGEST, verdict.effectiveDeviceTime());
    }

    @Test
    @DisplayName("恰好一分钟偏差仍为 NORMAL，边界不误判")
    void should_treat_exact_tolerance_as_normal() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(1, 0), INGEST);
        assertEquals(TimeQuality.NORMAL, verdict.quality());
    }

    @Test
    @DisplayName("恰好五分钟偏差不算 FUTURE，落在 CLOCK_DRIFT")
    void should_treat_exact_future_tolerance_as_drift() {
        PositionTimeVerdict verdict = POLICY.evaluate(local(5, 0), INGEST);
        assertEquals(TimeQuality.CLOCK_DRIFT, verdict.quality());
    }

    /**
     * 以接入时间为基准加减分钟数构造设备时间，避免测试里到处写时区换算。
     */
    private LocalDateTime local(int addMinutes, int addSeconds) {
        Instant device = INGEST.plus(Duration.ofMinutes(addMinutes)).plusSeconds(addSeconds);
        return LocalDateTime.ofInstant(device, ZONE);
    }
}
