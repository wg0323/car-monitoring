package com.cm.infrastructure.telemetry;

import com.cm.domain.track.TrackQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轨迹查询真库测试（TC-CMD-19 / TC-DAT-01 的仓储侧）：按车与时间段过滤、按设备时间升序、
 * 另一个车辆或 SIM 的点不得混入，列表截断与 count 使用同一条件。
 */
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.mongodb.uri=${CM_P3_MONGO_URI:mongodb://127.0.0.1:27017/cm_p3_telemetry_test}",
        "cm.telemetry.mongo.history-collection=position_track_test",
        "cm.telemetry.mongo.quarantine-collection=position_track_quarantine_test",
        "cm.telemetry.mongo.auto-init=true"
})
@Import({PositionHistoryRepository.class, PositionQuarantineRepository.class, PositionTrackRepository.class})
class PositionTrackRepositoryTest {

    private static final String COLLECTION = "position_track_test";

    private static final String QUARANTINE = "position_track_quarantine_test";

    private static final long VEHICLE_ID = 123L;

    private static final String SIM_NO = "13800138000";

    private static final Instant BASE = Instant.parse("2026-11-15T04:00:00Z");

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PositionHistoryRepository historyRepository;

    @Autowired
    private PositionQuarantineRepository quarantineRepository;

    @Autowired
    private PositionTrackRepository trackRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(QUARANTINE);
        historyRepository.ensureCollection();
        quarantineRepository.ensureCollection();
    }

    @Test
    @DisplayName("按车与时间段过滤，按设备时间升序返回全部点")
    void should_return_points_of_vehicle_in_time_order() {
        insertPoint("E1", BASE.plusSeconds(120), VEHICLE_ID, SIM_NO);
        insertPoint("E2", BASE, VEHICLE_ID, SIM_NO);
        insertPoint("E3", BASE.plusSeconds(60), VEHICLE_ID, SIM_NO);

        TrackQuery query = query(VEHICLE_ID, SIM_NO, BASE, BASE.plusSeconds(120));
        List<TrackPoint> points = trackRepository.find(query, 100);

        assertEquals(3L, trackRepository.count(query));
        assertEquals(List.of("E2", "E3", "E1"), points.stream().map(TrackPoint::eventId).toList());
        assertEquals(30000000, points.get(0).latitude());
        assertEquals(120000000, points.get(0).longitude());
        assertEquals(60, points.get(0).speed());
    }

    @Test
    @DisplayName("其他车辆与换绑后同一 SIM 的点不混入")
    void should_exclude_points_of_other_vehicles() {
        insertPoint("E1", BASE, VEHICLE_ID, SIM_NO);
        insertPoint("X1", BASE, 999L, SIM_NO);
        insertPoint("X2", BASE, VEHICLE_ID, "13900139000");

        TrackQuery query = query(VEHICLE_ID, SIM_NO, BASE, BASE.plusSeconds(60));

        assertEquals(1L, trackRepository.count(query));
        assertEquals(List.of("E1"), trackRepository.find(query, 100).stream().map(TrackPoint::eventId).toList());
    }

    @Test
    @DisplayName("时间边界包含起止时刻，范围外不返回")
    void should_include_range_boundaries_only() {
        insertPoint("E1", BASE.minusSeconds(1), VEHICLE_ID, SIM_NO);
        insertPoint("E2", BASE, VEHICLE_ID, SIM_NO);
        insertPoint("E3", BASE.plusSeconds(60), VEHICLE_ID, SIM_NO);
        insertPoint("E4", BASE.plusSeconds(61), VEHICLE_ID, SIM_NO);

        TrackQuery query = query(VEHICLE_ID, SIM_NO, BASE, BASE.plusSeconds(60));

        assertEquals(List.of("E2", "E3"), trackRepository.find(query, 100).stream()
                .map(TrackPoint::eventId).toList());
    }

    @Test
    @DisplayName("limit 只截断返回行，count 仍反映全量供导出上限判断")
    void should_truncate_rows_without_affecting_count() {
        for (int index = 0; index < 5; index++) {
            insertPoint("E" + index, BASE.plusSeconds(index), VEHICLE_ID, SIM_NO);
        }

        TrackQuery query = query(VEHICLE_ID, SIM_NO, BASE, BASE.plusSeconds(10));

        assertEquals(5L, trackRepository.count(query));
        assertEquals(2, trackRepository.find(query, 2).size());
    }

    @Test
    @DisplayName("空区间返回 0 条")
    void should_return_empty_when_no_point() {
        insertPoint("E1", BASE, VEHICLE_ID, SIM_NO);

        TrackQuery query = query(VEHICLE_ID, SIM_NO, BASE.plusSeconds(3600), BASE.plusSeconds(7200));

        assertEquals(0L, trackRepository.count(query));
        assertTrue(trackRepository.find(query, 100).isEmpty());
    }

    private void insertPoint(String eventId, Instant deviceTime, long vehicleId, String simNo) {
        PositionSample sample = new PositionSample(0, 3, 30000000, 120000000, 15, 60, 90, 1234L, 56, 58, 4, 2400);
        PositionEvent event = new PositionEvent(PositionEvent.SCHEMA_VERSION, eventId, PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, simNo, 1, deviceTime.toString(), deviceTime.toString(),
                deviceTime.toString(), "NORMAL", "gw-test", eventId, 0, sample, "digest");
        historyRepository.insert(event, new VehicleBinding(vehicleId, 1L, "粤B12345", 1, 9L));
    }

    private TrackQuery query(long vehicleId, String simNo, Instant start, Instant end) {
        return new TrackQuery(vehicleId, simNo,
                LocalDateTime.ofInstant(start, ZONE), LocalDateTime.ofInstant(end, ZONE));
    }
}
