package com.cm.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cm.protocol.body.RegistrationResponse;

/**
 * 0x8100 注册应答的逐字节回归：拿 P0 冻结的 DL-8100-01/02 真实下行帧反向编码，
 * 输出必须与原帧完全一致（含转义与校验码），同时验证字段解析口径。
 */
class RegistrationResponseGoldenTest {

    private static final String FRAMES_RESOURCE = "/golden/frames.csv";

    private static final int FIELD_COUNT = 9;

    private static final int HEX_FIELD_INDEX = 7;

    private static final String AUTH_CODE = "1234567890A";

    private static final String SIM_NO = "18301036442";

    private static final Map<String, byte[]> FRAMES = new HashMap<>();

    @BeforeAll
    static void loadFrames() throws IOException {
        try (InputStream stream = RegistrationResponseGoldenTest.class.getResourceAsStream(FRAMES_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺少测试资源 " + FRAMES_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (String line : reader.lines().toList()) {
                    if (line.isBlank() || line.startsWith("caseId,")) {
                        continue;
                    }
                    String[] parts = line.split(",", FIELD_COUNT);
                    if (parts.length >= FIELD_COUNT) {
                        FRAMES.put(parts[0], toBytes(parts[HEX_FIELD_INDEX]));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("重新编码 DL-8100-01 与真实下行帧逐字节相等")
    void should_encode_golden_frame_byte_for_byte() {
        byte[] golden = requireFrame("DL-8100-01");

        byte[] encoded = Jt808Encoder.registrationAck(SIM_NO, 3, 5,
                RegistrationResponse.RESULT_SUCCESS, AUTH_CODE);

        assertArrayEquals(golden, encoded);
    }

    @Test
    @DisplayName("第二条黄金帧同样逐字节相等，且应答流水号跟随注册帧")
    void should_encode_second_golden_frame() {
        byte[] golden = requireFrame("DL-8100-02");

        byte[] encoded = Jt808Encoder.registrationAck(SIM_NO, 5, 7,
                RegistrationResponse.RESULT_SUCCESS, AUTH_CODE);

        assertArrayEquals(golden, encoded);
    }

    @Test
    @DisplayName("黄金帧消息体可反解为注册流水号、结果与鉴权码")
    void should_parse_golden_body() {
        byte[] inner = FrameEscaper.unescape(FrameEscaper.stripDelimiters(requireFrame("DL-8100-01")));
        MessageHeader header = MessageHeader.parse(inner, 0);

        RegistrationResponse response = RegistrationResponse.parse(inner, header.length(), header.bodySize());

        assertEquals(RegistrationResponse.MESSAGE_ID, header.messageId());
        assertEquals(SIM_NO, header.simNo());
        assertEquals(5, response.registerSerialNo());
        assertEquals(RegistrationResponse.RESULT_SUCCESS, response.result());
        assertEquals(AUTH_CODE, response.authCode());
    }

    @Test
    @DisplayName("失败结果不带鉴权码，消息体只有 3 字节")
    void should_not_append_auth_code_when_failed() {
        byte[] frame = Jt808Encoder.registrationAck(SIM_NO, 9, 11,
                RegistrationResponse.RESULT_VEHICLE_NOT_FOUND, AUTH_CODE);
        byte[] inner = FrameEscaper.unescape(FrameEscaper.stripDelimiters(frame));
        MessageHeader header = MessageHeader.parse(inner, 0);

        RegistrationResponse response = RegistrationResponse.parse(inner, header.length(), header.bodySize());

        assertEquals(3, header.bodySize());
        assertEquals(11, response.registerSerialNo());
        assertEquals(RegistrationResponse.RESULT_VEHICLE_NOT_FOUND, response.result());
        assertEquals("", response.authCode());
    }

    @Test
    @DisplayName("消息体不足 3 字节时拒绝解析")
    void should_reject_short_body() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationResponse.parse(new byte[] {0x00, 0x05}, 0, 2));
    }

    private static byte[] requireFrame(String caseId) {
        byte[] frame = FRAMES.get(caseId);
        if (frame == null) {
            throw new IllegalStateException("黄金报文缺少用例 " + caseId);
        }
        return frame;
    }

    private static byte[] toBytes(String hex) {
        String normalized = hex.trim();
        byte[] bytes = new byte[normalized.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(normalized.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }
}
