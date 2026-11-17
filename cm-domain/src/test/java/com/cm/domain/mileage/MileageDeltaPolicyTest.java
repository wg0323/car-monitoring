package com.cm.domain.mileage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 里程差值：正常增量、缺附加项、无基线、归零重置、单点跳变过大。
 */
class MileageDeltaPolicyTest {

    private static final long MAX_DELTA_RAW = 100L * MileageDeltaPolicy.RAW_PER_KILOMETER;

    @Test
    @DisplayName("正常增量按原始单位相减")
    void should_return_delta_for_normal_step() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(1000L, 1050L, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.NORMAL, delta.status());
        assertEquals(50L, delta.deltaRaw());
        assertTrue(delta.counted());
    }

    @Test
    @DisplayName("恰好等于上限仍按正常增量")
    void should_accept_delta_equal_to_limit() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(1000L, 1000L + MAX_DELTA_RAW, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.NORMAL, delta.status());
        assertEquals(MAX_DELTA_RAW, delta.deltaRaw());
    }

    @Test
    @DisplayName("超过上限按脏数据处理，增量为 0")
    void should_reject_oversized_delta() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(1000L, 1000L + MAX_DELTA_RAW + 1, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.ABNORMAL, delta.status());
        assertEquals(0L, delta.deltaRaw());
        assertFalse(delta.counted());
    }

    @Test
    @DisplayName("累计值变小按重置处理，不产生负增量")
    void should_treat_decrease_as_reset() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(5000L, 10L, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.RESET, delta.status());
        assertEquals(0L, delta.deltaRaw());
    }

    @Test
    @DisplayName("同一累计值重复上报时增量为 0，不算计入")
    void should_not_count_zero_delta() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(1000L, 1000L, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.NORMAL, delta.status());
        assertEquals(0L, delta.deltaRaw());
        assertFalse(delta.counted());
    }

    @Test
    @DisplayName("没有基线时当前值只能作基线")
    void should_treat_first_value_as_baseline() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(0L, 5000L, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.BASELINE, delta.status());
        assertEquals(0L, delta.deltaRaw());
    }

    @Test
    @DisplayName("报文未带 0x01 附加项时不参与统计")
    void should_ignore_missing_additional() {
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(5000L, 0L, MAX_DELTA_RAW);

        assertEquals(MileageDeltaPolicy.Status.MISSING, delta.status());
        assertEquals(0L, delta.deltaRaw());
    }
}
