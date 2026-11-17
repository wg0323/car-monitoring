package com.cm.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cm.protocol.body.GenericAck;
import com.cm.protocol.body.PositionReport;

/**
 * P0 冻结黄金报文的差分测试：同一批帧输入，逐字段比对 decoder-snapshot.txt。
 * 除 $ 私有帧的三处已知差异外，其余上行帧要求与新实现逐字节段一致。
 */
class Jt808DecoderGoldenTest {

    private static final String FRAMES_RESOURCE = "/golden/frames.csv";

    private static final String SNAPSHOT_RESOURCE = "/golden/decoder-snapshot.txt";

    private static final String UPLINK = "uplink";

    private static final int FIELD_COUNT = 9;

    private static final int HEX_FIELD_INDEX = 7;

    private static final String CASE_SEPARATOR = "=== ";

    private static final String CASE_SUFFIX = " ===";

    private static final String PRIVATE_CASE = "SYN-DOLLAR-01";

    private static final Map<String, String> FRAMES = new LinkedHashMap<>();

    private static final Map<String, String> SNAPSHOTS = new HashMap<>();

    @BeforeAll
    static void loadGoldenResources() throws IOException {
        loadFrames();
        loadSnapshots();
    }

    @Test
    @DisplayName("上行与合成帧逐字段等于冻结快照，$ 私有帧单独裁决")
    void strictCasesMatchFrozenSnapshot() {
        List<String> strictCases = FRAMES.keySet().stream()
                .filter(caseId -> !PRIVATE_CASE.equals(caseId))
                .toList();
        assertTrue(strictCases.size() >= 15, "黄金用例数量不足，检查 frames.csv 是否完整");
        for (String caseId : strictCases) {
            Jt808Message message = Jt808Decoder.decode(toBytes(FRAMES.get(caseId)));
            String expected = SNAPSHOTS.get(caseId);
            assertNotNull(expected, "快照缺少用例 " + caseId);
            assertEquals(expected.trim(), GoldenSnapshotRenderer.render(caseId, message).trim(),
                    "用例 " + caseId + " 解码结果与 P0 冻结快照不一致");
        }
    }

    @Test
    @DisplayName("$ 私有帧：旧实现把帧头前 2 字节误当流水号，新实现按帧尾读取")
    void privateFrameSerialFollowsSpecificationInsteadOfLegacyBug() {
        String legacy = SNAPSHOTS.get(PRIVATE_CASE);
        assertTrue(legacy.contains("serialNo=313"), "P0 快照应记录旧实现的 313");
        assertTrue(legacy.contains("frameHex=\n"), "P0 快照未记录旧实现的帧字节");

        Jt808Message message = Jt808Decoder.decodePrivate(toBytes(FRAMES.get(PRIVATE_CASE)));
        assertFalse(message.failed());
        assertEquals("0139123456", message.simNo());
        assertEquals(0, message.header().serialNo());
        assertEquals(toBytes(FRAMES.get(PRIVATE_CASE)).length * 2, message.frameHex().length());

        PositionReport position = (PositionReport) message.body();
        assertEquals(26092055, position.latitude());
        assertEquals(119386991, position.longitude());
        assertEquals(60, position.speed());
        assertEquals(30, position.course());
        assertEquals(1, position.status());
        assertEquals("2026-11-14T17:30:15", position.deviceTime().toString());
    }

    @Test
    @DisplayName("校验码错误的帧被拒绝且不解析消息体")
    void checksumMismatchIsRejected() {
        byte[] frame = toBytes(FRAMES.get("UL-0200-01"));
        frame[frame.length - 2] ^= 0x01;
        Jt808Message message = Jt808Decoder.decode(frame);
        assertTrue(message.failed());
        assertEquals("校验码不正确", message.errorMessage());
        assertNull(message.header());
        assertNull(message.body());
    }

    @Test
    @DisplayName("应答消息 ID 在模型中保留无符号语义")
    void genericAckKeepsUnsignedMessageId() {
        Jt808Message message = Jt808Decoder.decode(toBytes(FRAMES.get("UL-0001-01")));
        GenericAck ack = (GenericAck) message.body();
        assertEquals(0x8103, ack.responseMessageId());
        assertEquals(94, ack.responseSerialNo());
        assertEquals(0, ack.result());
    }

    @Test
    @DisplayName("分包帧只保留 childPacket，不解析消息体")
    void packagedFrameKeepsChildPacketOnly() {
        Jt808Message message = Jt808Decoder.decode(toBytes(FRAMES.get("SYN-0801-01")));
        assertTrue(message.header().packaged());
        assertEquals(2, message.header().totalPackets());
        assertEquals(1, message.header().packetNo());
        assertNull(message.body());
        assertEquals(42, message.childPacket().length);
    }

    @Test
    @DisplayName("多媒体检索应答的布局在旧实现里会越界报解析异常，本阶段保持同样的可观测结果")
    void mediaSearchAckStaysUndecodedUntilMultimediaPhase() {
        Jt808Message message = Jt808Decoder.decode(toBytes(FRAMES.get("UL-0802-01")));
        assertTrue(message.failed());
        assertEquals("解析异常", message.errorMessage());
        assertTrue(SNAPSHOTS.get("UL-0802-01").contains("error=解析异常"));
    }

    private static void loadFrames() throws IOException {
        for (String line : readResource(FRAMES_RESOURCE)) {
            if (line.isBlank() || line.startsWith("caseId,")) {
                continue;
            }
            String[] parts = line.split(",", FIELD_COUNT);
            if (parts.length < FIELD_COUNT) {
                throw new IllegalStateException("frames.csv 行格式异常: " + line);
            }
            if (UPLINK.equals(parts[1])) {
                FRAMES.put(parts[0], parts[HEX_FIELD_INDEX]);
            }
        }
    }

    private static void loadSnapshots() throws IOException {
        StringBuilder current = new StringBuilder();
        String currentCase = null;
        for (String line : readResource(SNAPSHOT_RESOURCE)) {
            if (line.startsWith(CASE_SEPARATOR) && line.endsWith(CASE_SUFFIX)) {
                if (currentCase != null) {
                    SNAPSHOTS.put(currentCase, current.toString());
                }
                currentCase = line.substring(CASE_SEPARATOR.length(), line.length() - CASE_SUFFIX.length());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (currentCase != null) {
            SNAPSHOTS.put(currentCase, current.toString());
        }
    }

    private static List<String> readResource(String resource) throws IOException {
        try (InputStream stream = Jt808DecoderGoldenTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("缺少测试资源 " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
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
