package com.cm.admin.monitor;

import com.cm.infrastructure.telemetry.PositionBroadcast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorFramesTest {

    private static final long VEHICLE_ID = 100000000000000001L;

    @Test
    @DisplayName("位置帧把 808 原始单位换算成度与公里每小时")
    void should_convert_raw_units_to_degrees_and_kph() {
        PositionBroadcast event = event(30000000, 120000000, 603, 90);

        MonitorFrames.PositionFrame frame = MonitorFrames.position(event, VEHICLE_ID, true);

        assertEquals(30.0, frame.latitude());
        assertEquals(120.0, frame.longitude());
        assertEquals(60.3, frame.speedKph());
        assertEquals(90, frame.course());
        assertTrue(frame.online());
    }

    @Test
    @DisplayName("车辆 ID 序列化为字符串，避免浏览器丢精度")
    void should_serialize_vehicle_id_as_string() {
        String json = MonitorFrames.write(MonitorFrames.position(event(30000000, 120000000, 0, 0),
                VEHICLE_ID, false));

        assertTrue(json.contains("\"vehicleId\":\"" + VEHICLE_ID + "\""), json);
    }

    @Test
    @DisplayName("上行帧按字符串接收车辆 ID")
    void should_read_inbound_frame_with_string_ids() {
        MonitorFrames.InboundFrame frame = MonitorFrames.read(
                "{\"type\":\"SUBSCRIBE\",\"vehicleIds\":[\"100000000000000001\"]}");

        assertEquals(MonitorFrames.TYPE_SUBSCRIBE, frame.type());
        assertEquals(List.of("100000000000000001"), frame.vehicleIds());
    }

    @Test
    @DisplayName("错误帧带业务码")
    void should_write_error_frame() {
        String json = MonitorFrames.write(MonitorFrames.error(MonitorFrames.CODE_OUT_OF_SCOPE, "已忽略"));

        assertTrue(json.contains("\"code\":4403"), json);
        assertTrue(json.contains("\"type\":\"ERROR\""), json);
    }

    private PositionBroadcast event(int latitude, int longitude, int speed, int course) {
        return new PositionBroadcast(1, "13800138000", latitude, longitude, speed, course, 0, 1,
                "2026-11-15T04:00:00Z");
    }
}
