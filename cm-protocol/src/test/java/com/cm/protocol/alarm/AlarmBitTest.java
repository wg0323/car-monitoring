package com.cm.protocol.alarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报警位定义：32 位全覆盖、位序号即 alarm_type 取值、越界返回空。
 */
class AlarmBitTest {

    private static final int OUT_OF_RANGE = 32;

    @Test
    @DisplayName("32 个报警位全部有定义，位序号与 code 一致")
    void should_define_all_32_bits() {
        AlarmBit[] bits = AlarmBit.values();
        assertEquals(AlarmBit.BIT_COUNT, bits.length);
        for (AlarmBit item : bits) {
            assertEquals(Integer.toString(item.bit()), item.code());
            assertFalse(item.description().isBlank());
            assertTrue(AlarmBit.of(item.bit()).isPresent());
        }
    }

    @Test
    @DisplayName("边界位 0 / 20 / 21 / 31 可查")
    void should_resolve_boundary_bits() {
        assertEquals(AlarmBit.EMERGENCY, AlarmBit.of(0).orElseThrow());
        assertEquals(AlarmBit.IN_OUT_AREA, AlarmBit.of(20).orElseThrow());
        assertEquals(AlarmBit.IN_OUT_ROUTE, AlarmBit.of(21).orElseThrow());
        assertEquals(AlarmBit.RESERVED_31, AlarmBit.of(31).orElseThrow());
        assertEquals("20", AlarmBit.IN_OUT_AREA.code());
    }

    @Test
    @DisplayName("越界位返回空且描述为空串")
    void should_reject_out_of_range_bit() {
        assertTrue(AlarmBit.of(-1).isEmpty());
        assertTrue(AlarmBit.of(OUT_OF_RANGE).isEmpty());
        assertEquals("", AlarmBit.descriptionOf(OUT_OF_RANGE));
    }
}
