package com.cm.protocol.jt809;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cm.protocol.jt809.body.MainLinkLogin;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import com.cm.protocol.jt809.body.SubLinkLogin;
import com.cm.protocol.jt809.body.SubLinkLoginAck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 主从链路四条帧的逐字节回归。
 * 帧与 CRC 均摘自 test-evidence/logs/L3_2026-11-14_809-mylog.txt 的实测记录：
 * 0x1001 尾部 0809、0x1002 尾部 2577、0x9001 尾部 B0BC、0x9002 尾部 BEAA；
 * 长度字段 0x48 / 0x1F / 0x1E / 0x1B 也与日志一致。重建后与原帧相等即证明字段偏移与校验口径都正确。
 */
class Jt809GoldenFrameTest {

    private static final long CENTER_ID = 8992L;

    private static final long USER_ID = 8992L;

    private static final String PASSWORD = "12345678";

    private static final String SUB_LINK_IP = "127.0.0.1";

    private static final int SUB_LINK_PORT = 12345;

    private static final String VERSION_HEX = "0001E0";

    private static final String IP_PADDING = "00".repeat(23);

    private static final String SUB_LINK_IP_HEX = "3132372E302E302E31" + IP_PADDING;

    @Test
    @DisplayName("0x1001 主链路登录请求重建后与实测帧逐字节相等")
    void should_rebuild_main_link_login_frame() {
        String expected = "5B" + "00000048" + "00000000" + "1001" + "00002320" + VERSION_HEX
                + "00" + "00000000"
                + "00002320" + "3132333435363738" + SUB_LINK_IP_HEX + "3039"
                + "0809" + "5D";

        byte[] encoded = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_LOGIN, 0L, CENTER_ID,
                new MainLinkLogin(USER_ID, PASSWORD, SUB_LINK_IP, SUB_LINK_PORT).encode());

        assertArrayEquals(hexToBytes(expected), encoded);
    }

    @Test
    @DisplayName("0x1002 主链路登录应答重建后与实测帧逐字节相等，并可反解结果与校验码")
    void should_rebuild_main_link_login_ack_frame() {
        String expected = "5B" + "0000001F" + "00000000" + "1002" + "00002320" + VERSION_HEX
                + "00" + "00000000" + "00" + "00002320" + "2577" + "5D";

        byte[] encoded = Jt809FrameCodec.encode(Jt809Constants.MSG_MAIN_LINK_LOGIN_ACK, 0L, CENTER_ID,
                new MainLinkLoginAck(MainLinkLoginAck.RESULT_SUCCESS, CENTER_ID).encode());

        assertArrayEquals(hexToBytes(expected), encoded);
        MainLinkLoginAck ack = MainLinkLoginAck.parse(Jt809FrameCodec.decode(encoded).body());
        assertEquals(MainLinkLoginAck.RESULT_SUCCESS, ack.result());
        assertEquals(CENTER_ID, ack.verifyCode());
    }

    @Test
    @DisplayName("0x9001 从链路连接请求重建后与实测帧逐字节相等")
    void should_rebuild_sub_link_login_frame() {
        String expected = "5B" + "0000001E" + "00000001" + "9001" + "00002320" + VERSION_HEX
                + "00" + "00000000" + "00002320" + "B0BC" + "5D";

        byte[] encoded = Jt809FrameCodec.encode(Jt809Constants.MSG_SUB_LINK_LOGIN, 1L, CENTER_ID,
                new SubLinkLogin(CENTER_ID).encode());

        assertArrayEquals(hexToBytes(expected), encoded);
    }

    @Test
    @DisplayName("0x9002 从链路连接应答重建后与实测帧逐字节相等")
    void should_rebuild_sub_link_login_ack_frame() {
        String expected = "5B" + "0000001B" + "00000001" + "9002" + "00002320" + VERSION_HEX
                + "00" + "00000000" + "00" + "BEAA" + "5D";

        byte[] encoded = Jt809FrameCodec.encode(Jt809Constants.MSG_SUB_LINK_LOGIN_ACK, 1L, CENTER_ID,
                new SubLinkLoginAck(SubLinkLoginAck.RESULT_SUCCESS).encode());

        assertArrayEquals(hexToBytes(expected), encoded);
    }

    @Test
    @DisplayName("0x1001 实测帧可反解出账号、密码、从链路地址与端口")
    void should_parse_main_link_login_frame() {
        String hex = "5B" + "00000048" + "00000000" + "1001" + "00002320" + VERSION_HEX
                + "00" + "00000000"
                + "00002320" + "3132333435363738" + SUB_LINK_IP_HEX + "3039"
                + "0809" + "5D";

        Jt809Frame frame = Jt809FrameCodec.decode(hexToBytes(hex));
        MainLinkLogin login = MainLinkLogin.parse(frame.body());

        assertEquals(Jt809Constants.MSG_MAIN_LINK_LOGIN, frame.messageId());
        assertEquals(0L, frame.messageSn());
        assertEquals(CENTER_ID, frame.centerId());
        assertEquals(USER_ID, login.userId());
        assertEquals(PASSWORD, login.password());
        assertEquals(SUB_LINK_IP, login.subLinkIp());
        assertEquals(SUB_LINK_PORT, login.subLinkPort());
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }
}
 
