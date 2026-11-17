package com.cm.infrastructure.telemetry;

import com.cm.infrastructure.command.CmCommandConfiguration;
import com.cm.infrastructure.config.CmIdConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 报警落库：开户幂等、关户、关后再开、持续上报不重复开户、重启后按库对齐。
 * 默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = AlarmRecordWriterTest.TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
class AlarmRecordWriterTest {

    private static final String SIM_PREFIX = "P3-ALM-";

    private static final String PLATE_NO = "测试A0001";

    private static final int OVER_SPEED_BIT = 1;

    private static final Instant START = Instant.parse("2026-11-15T02:00:00Z");

    private static final long GAP_SECONDS = 120L;

    private static final int DEFAULT_SPEED = 600;

    private static final VehicleBinding BINDING = new VehicleBinding(9001L, 7L, PLATE_NO, 2, 11L);

    @Autowired
    private AlarmRecordWriter writer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 每个用例用独立 SIM：AlarmRecordWriter 的每车状态缓存在单例 Bean 里，共用 SIM 会把上一个用例的基线带进下一个。
     */
    private String simNo;

    @BeforeEach
    void setUp() {
        simNo = SIM_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("同一帧重复投递只开一条报警")
    void should_open_alarm_only_once() {
        writer.apply(event(START, flag(OVER_SPEED_BIT)), BINDING);
        writer.apply(event(START, flag(OVER_SPEED_BIT)), BINDING);

        assertEquals(1, countRows());
        Map<String, Object> row = singleRow();
        assertEquals("1", row.get("alarm_type"));
        assertEquals("terminal_alarm", row.get("alarm_source"));
        assertEquals("New", row.get("alarm_status"));
        assertEquals(PLATE_NO, row.get("plate_no"));
        assertEquals(11L, ((Number) row.get("department_id")).longValue());
        assertNull(row.get("end_time"));
    }

    @Test
    @DisplayName("位持续置位时不重复开户")
    void should_not_open_twice_when_bit_keeps_set() {
        writer.apply(event(START, flag(OVER_SPEED_BIT)), BINDING);
        writer.apply(event(START.plusSeconds(GAP_SECONDS), flag(OVER_SPEED_BIT)), BINDING);

        assertEquals(1, countRows());
    }

    @Test
    @DisplayName("位清零时关户，持续时长按设备时间算")
    void should_close_alarm_with_time_span() {
        writer.apply(event(START, flag(OVER_SPEED_BIT)), BINDING);
        writer.apply(event(START.plusSeconds(GAP_SECONDS), 0), BINDING);

        Map<String, Object> row = singleRow();
        assertEquals("Old", row.get("alarm_status"));
        assertNotNull(row.get("end_time"));
        assertEquals(2.0D, ((Number) row.get("time_span")).doubleValue(), 0.001D);
    }

    @Test
    @DisplayName("关户后再次置位会开新的一户，event_key 不同")
    void should_open_new_alarm_after_closed() {
        writer.apply(event(START, flag(OVER_SPEED_BIT)), BINDING);
        writer.apply(event(START.plusSeconds(GAP_SECONDS), 0), BINDING);
        writer.apply(event(START.plusSeconds(GAP_SECONDS * 2), flag(OVER_SPEED_BIT)), BINDING);

        assertEquals(2, countRows());
        List<String> keys = jdbcTemplate.queryForList(
                "SELECT event_key FROM cm_alarm_record WHERE sim_no = ? ORDER BY start_time", String.class, simNo);
        assertEquals(2, keys.stream().distinct().count());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cm_alarm_record WHERE sim_no = ? AND end_time IS NULL",
                Integer.class, simNo));
    }

    @Test
    @DisplayName("重启后首帧对齐：库里已开而当前位已清时关户")
    void should_close_stale_alarm_on_first_frame() {
        jdbcTemplate.update("INSERT INTO cm_alarm_record (id, event_key, alarm_channel, sim_no, plate_no,"
                + " alarm_source, alarm_type, start_time, alarm_status, is_processed, is_deleted)"
                + " VALUES (?, ?, 'TERMINAL', ?, ?, 'terminal_alarm', '1', ?, 'New', 0, 0)",
                990001L, "ALM-T-" + simNo + "-1-1", simNo, PLATE_NO, START.minusSeconds(600));
        writer.apply(event(START, 0), BINDING);

        Map<String, Object> row = singleRow();
        assertEquals("Old", row.get("alarm_status"));
        assertNotNull(row.get("end_time"));
    }

    @Test
    @DisplayName("停车报警：低速开户、恢复行驶关户")
    void should_open_and_close_parking_alarm() {
        writer.apply(event(START, 0, 0), BINDING);

        Map<String, Object> opened = singleRow();
        assertEquals("Parking", opened.get("alarm_type"));
        assertEquals("PLATFORM", opened.get("alarm_channel"));
        assertEquals("platform_alarm", opened.get("alarm_source"));
        assertEquals("New", opened.get("alarm_status"));

        writer.apply(event(START.plusSeconds(GAP_SECONDS), 0, DEFAULT_SPEED), BINDING);

        Map<String, Object> closed = singleRow();
        assertEquals("Old", closed.get("alarm_status"));
        assertEquals(2.0D, ((Number) closed.get("time_span")).doubleValue(), 0.001D);
    }

    @Test
    @DisplayName("持续停车不重复开户")
    void should_not_open_parking_twice() {
        writer.apply(event(START, 0, 0), BINDING);
        writer.apply(event(START.plusSeconds(GAP_SECONDS), 0, 0), BINDING);

        assertEquals(1, countRows());
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM cm_alarm_record WHERE sim_no LIKE ?", SIM_PREFIX + "%");
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cm_alarm_record WHERE sim_no = ?", Integer.class, simNo);
        return count == null ? 0 : count;
    }

    private Map<String, Object> singleRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM cm_alarm_record WHERE sim_no = ?", simNo);
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private PositionEvent event(Instant deviceTime, int alarmFlag) {
        return event(deviceTime, alarmFlag, DEFAULT_SPEED);
    }

    private PositionEvent event(Instant deviceTime, int alarmFlag, int speed) {
        PositionSample sample = new PositionSample(alarmFlag, 1, 30200000, 120000000, 100, speed, 90,
                12000L, 300, 550, 26, 240);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, "E-" + deviceTime.getEpochSecond(),
                PositionEvent.EVENT_TYPE, PositionEvent.BRANCH_STANDARD, simNo, 1,
                deviceTime.toString(), deviceTime.toString(), deviceTime.toString(), "NORMAL",
                "gw-test", "trace-1", 0, sample, "digest");
    }

    private static int flag(int bit) {
        return 1 << bit;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.cm.infrastructure.service")
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import({TelemetryConfiguration.class, TelemetryMetrics.class, AlarmRecordWriter.class,
            CmCommandConfiguration.class, CmIdConfiguration.class})
    static class TestApplication {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
