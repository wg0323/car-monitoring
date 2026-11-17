package com.cm.protocol.jt809;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 帧层口径：长度字段等于全帧字节数、CRC 覆盖 5B 与 5D 之间的全部字节、
 * 转义只作用于帧内内容，首尾标识位不参与。
 */
class Jt809FrameCodecTest {

    private static final long MESSAGE_SN = 7L;

    private static final long CENTER_ID = 8992L;

    @Test
    @DisplayName("编码后长度字段等于全帧字节数，且能被原样解码")
    void should_declare_total_frame_length_and_round_trip() {
        byte[] body = {0x01, 0x02, 0x03, 0x04};

        byte[] frame = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_TEST, MESSAGE_SN, CENTER_ID, body);

        assertEquals(body.length + Jt809Constants.FIXED_OVERHEAD, frame.length);
        assertEquals(frame.length, Jt809FrameCodec.declaredLength(frame, 0));

        Jt809Frame decoded = Jt809FrameCodec.decode(frame);
        assertEquals(Jt809Constants.MSG_MAIN_LINK_TEST, decoded.messageId());
        assertEquals(MESSAGE_SN, decoded.messageSn());
        assertEquals(CENTER_ID, decoded.centerId());
        assertEquals(Jt809Constants.UNENCRYPTED, decoded.encryptFlag());
        assertArrayEquals(body, decoded.body());
    }

    @Test
    @DisplayName("消息体中的 5A/5B/5D/5E 全部转义，解码后还原原字节")
    void should_escape_control_bytes_inside_body() {
        byte[] body = {0x5A, 0x5B, 0x5D, 0x5E};

        byte[] frame = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_TEST, MESSAGE_SN, CENTER_ID, body);

        assertEquals(body.length * 2 + Jt809Constants.FIXED_OVERHEAD, frame.length);
        assertArrayEquals(body, Jt809FrameCodec.decode(frame).body());
    }

    @Test
    @DisplayName("CRC 不匹配的帧被拒绝")
    void should_reject_frame_with_broken_checksum() {
        byte[] frame = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_TEST, MESSAGE_SN, CENTER_ID,
                new byte[] {0x11, 0x22});
        frame[frame.length - 2] ^= 0x01;

        assertThrows(IllegalArgumentException.class, () -> Jt809FrameCodec.decode(frame));
    }

    @Test
    @DisplayName("长度字段与实际字节数不一致时被拒绝")
    void should_reject_frame_with_wrong_declared_length() {
        byte[] frame = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_TEST, MESSAGE_SN, CENTER_ID,
                new byte[] {0x11, 0x22});
        frame[3] += 1;

        assertThrows(IllegalArgumentException.class, () -> Jt809FrameCodec.decode(frame));
    }

    @Test
    @DisplayName("缺首尾标识位、过短帧与非法转义对均被拒绝")
    void should_reject_illegal_frames() {
        byte[] frame = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_TEST, MESSAGE_SN, CENTER_ID, null);
        byte[] wrongHead = frame.clone();
        wrongHead[0] = 0x5C;
        byte[] illegalEscape = {Jt809Constants.HEAD_FLAG, 0x5A, 0x03, Jt809Constants.TAIL_FLAG};

        assertThrows(IllegalArgumentException.class, () -> Jt809FrameCodec.decode(new byte[] {0x5B, 0x5D}));
        assertThrows(IllegalArgumentException.class, () -> Jt809FrameCodec.decode(wrongHead));
        assertThrows(IllegalArgumentException.class, () -> Jt809FrameCodec.decode(illegalEscape));
    }

    @Test
    @DisplayName("版本字段默认为旧平台实测的 00 01 E0")
    void should_use_legacy_version_bytes_by_default() {
        byte[] frame = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_TEST, MESSAGE_SN, CENTER_ID, null);

        assertArrayEquals(Jt809Constants.LEGACY_VERSION, Jt809FrameCodec.decode(frame).version());
    }
}
 
