package com.cm.gateway.telemetry;

import com.cm.domain.position.PositionTimePolicy;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.protocol.body.PositionReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件组装：eventId 对同一帧稳定、对不同业务点与不同内容可区分，时间质量按阈值判定。
 */
class PositionEventAssemblerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Instant INGEST = Instant.parse("2026-11-15T04:00:00Z");

    private static final LocalDateTime DEVICE_TIME = LocalDateTime.ofInstant(INGEST.minusSeconds(2), ZONE);

    private static final String SIM_NO = "13800138000";

    private static final int MESSAGE_ID = 0x0200;

    private static final byte[] FRAME = new byte[] {0x7E, 0x02, 0x00, 0x00, 0x1E, 0x7E};

    private final PositionEventAssembler assembler = new PositionEventAssembler(
            new PositionTimePolicy(Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofMinutes(1), ZONE),
            new TelemetryProperties(), commandProperties());

    @Test
    @DisplayName("同一帧重复接入得到相同 eventId")
    void should_build_same_event_id_for_same_frame() {
        String first = assemble(DEVICE_TIME, PositionEvent.BRANCH_STANDARD, 0, FRAME).eventId();
        String second = assemble(DEVICE_TIME, PositionEvent.BRANCH_STANDARD, 0, FRAME).eventId();
        assertEquals(first, second);
    }

    @Test
    @DisplayName("批量定位不同下标得到不同 eventId")
    void should_build_distinct_event_id_for_different_batch_index() {
        String first = assemble(DEVICE_TIME, PositionEvent.BRANCH_BATCH, 0, FRAME).eventId();
        String second = assemble(DEVICE_TIME, PositionEvent.BRANCH_BATCH, 1, FRAME).eventId();
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("流水号与时间相同但内容不同时 eventId 仍可区分")
    void should_build_distinct_event_id_for_different_payload() {
        String first = assemble(DEVICE_TIME, PositionEvent.BRANCH_STANDARD, 0, FRAME).eventId();
        String second = assemble(DEVICE_TIME, PositionEvent.BRANCH_STANDARD, 0,
                new byte[] {0x7E, 0x02, 0x00, 0x00, 0x1F, 0x7E}).eventId();
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("标准、批量与私有帧使用各自的协议前缀")
    void should_use_branch_specific_protocol_prefix() {
        assertTrue(assemble(DEVICE_TIME, PositionEvent.BRANCH_STANDARD, 0, FRAME).eventId()
                .startsWith("jt808-2011:"));
        assertTrue(assemble(DEVICE_TIME, PositionEvent.BRANCH_BATCH, 0, FRAME).eventId()
                .startsWith("jt808-batch:"));
        assertTrue(assemble(DEVICE_TIME, PositionEvent.BRANCH_PRIVATE, 0, FRAME).eventId()
                .startsWith("jt808-private:"));
    }

    @Test
    @DisplayName("设备时间超前超过容忍阈值判 FUTURE")
    void should_mark_future_quality() {
        PositionEvent event = assemble(DEVICE_TIME.plusMinutes(10), PositionEvent.BRANCH_STANDARD, 0, FRAME);
        assertEquals("FUTURE", event.timeQuality());
        assertEquals(INGEST.toString(), event.effectiveDeviceTime());
    }

    @Test
    @DisplayName("正常报文的有效设备时间取设备时间，并带出附加项")
    void should_carry_sample_and_effective_time() {
        PositionEvent event = assemble(DEVICE_TIME, PositionEvent.BRANCH_STANDARD, 0, FRAME);
        assertEquals("NORMAL", event.timeQuality());
        assertEquals(DEVICE_TIME.atZone(ZONE).toInstant().toString(), event.effectiveDeviceTime());
        assertEquals(30000000, event.sample().latitude());
        assertEquals(120000000, event.sample().longitude());
        assertEquals("gw-test", event.gatewayId());
    }

    private PositionEvent assemble(LocalDateTime deviceTime, String branch, int batchIndex, byte[] frame) {
        PositionReport report = PositionReport.of(0, 1, 30000000, 120000000, 0, 60, 90, deviceTime, List.of());
        return assembler.assemble(SIM_NO, MESSAGE_ID, 7, report, branch, batchIndex, frame, INGEST);
    }

    private CmCommandProperties commandProperties() {
        CmCommandProperties properties = new CmCommandProperties();
        properties.setGatewayId("gw-test");
        return properties;
    }
}
