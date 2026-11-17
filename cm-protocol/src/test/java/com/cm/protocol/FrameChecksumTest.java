package com.cm.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameChecksumTest {

    @Test
    void should_return_xor_result_when_range_is_valid() {
        byte[] frame = {0x02, 0x00, 0x01, 0x03};

        assertEquals((byte) 0x00, FrameChecksum.xor(frame, 0, frame.length));
        assertEquals((byte) 0x03, FrameChecksum.xor(frame, 0, 3));
        assertTrue(FrameChecksum.matches(frame, 0, frame.length, (byte) 0x00));
        assertFalse(FrameChecksum.matches(frame, 0, frame.length, (byte) 0x01));
    }

    @Test
    void should_reject_invalid_range_when_input_is_out_of_bounds() {
        byte[] frame = {0x01, 0x02};

        assertThrows(IllegalArgumentException.class, () -> FrameChecksum.xor(frame, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> FrameChecksum.xor(frame, 1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> FrameChecksum.xor(null, 0, 0));
    }
}
