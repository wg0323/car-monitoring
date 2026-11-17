package com.cm.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 下行编码与转义边界测试。期望字节取自黄金报文中的 DL-8001-01（该校验码按未转义内容计算，可自洽验证）。
 */
class Jt808EncoderTest {

    private static final String DOWNLINK_ACK_HEX = "7E800100050185032596010BBE03A9000200AC7E";

    private static final String SIM_NO = "18503259601";

    private static final int SERIAL_NO = 3006;

    private static final int ACKED_SERIAL_NO = 937;

    private static final int ACKED_MESSAGE_ID = 0x0002;

    private static final byte[] ESCAPE_SAMPLE = { 0x01, 0x7E, 0x02, 0x7D, 0x03 };

    private static final byte[] ESCAPED_SAMPLE = { 0x01, 0x7D, 0x02, 0x02, 0x7D, 0x01, 0x03 };

    @Test
    @DisplayName("0x8001 通用应答编码结果等于黄金下行帧字节")
    void genericAckMatchesGoldenDownlinkFrame() {
        byte[] actual = Jt808Encoder.successAck(SIM_NO, SERIAL_NO, ACKED_SERIAL_NO, ACKED_MESSAGE_ID);
        assertEquals(DOWNLINK_ACK_HEX, GoldenSnapshotRenderer.upperHex(actual));
    }

    @Test
    @DisplayName("编码结果可被自身解码器识别为合法帧结构")
    void encodedFramePassesStructuralCheck() {
        byte[] frame = Jt808Encoder.successAck(SIM_NO, SERIAL_NO, ACKED_SERIAL_NO, ACKED_MESSAGE_ID);
        byte[] content = FrameEscaper.unescape(FrameEscaper.stripDelimiters(frame));
        byte expected = content[content.length - 1];
        assertEquals(expected, FrameChecksum.xor(content, 0, content.length - 1));
        assertEquals(0x8001, ProtocolBytes.unsignedShort(content, 0));
        assertEquals(5, ProtocolBytes.unsignedShort(content, 2) & 0x03FF);
    }

    @Test
    @DisplayName("转义与还原互为逆运算，且只处理 0x7E 与 0x7D")
    void escapeRoundTrip() {
        assertArrayEquals(ESCAPED_SAMPLE, FrameEscaper.escape(ESCAPE_SAMPLE));
        assertArrayEquals(ESCAPE_SAMPLE, FrameEscaper.unescape(ESCAPED_SAMPLE));
    }

    @Test
    @DisplayName("非法转义序列与悬空转义前缀被拒绝")
    void illegalEscapeSequenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FrameEscaper.unescape(new byte[] { 0x7D, 0x03 }));
        assertThrows(IllegalArgumentException.class, () -> FrameEscaper.unescape(new byte[] { 0x01, 0x7D }));
    }

    @Test
    @DisplayName("缺少标识位的字节流不被当作合法帧")
    void frameWithoutDelimitersIsRejected() {
        byte[] raw = DOWNLINK_ACK_HEX.substring(2, DOWNLINK_ACK_HEX.length() - 2).getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> FrameEscaper.stripDelimiters(raw));
    }
}
