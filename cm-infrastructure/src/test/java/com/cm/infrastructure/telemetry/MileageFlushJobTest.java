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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 里程落库：连续两次 flush 结果一致、当日里程等于起止累计差、跨天分两条。
 * 默认不跑，同时设置 CM_P3_DB_TEST 与 CM_P3_REDIS_TEST 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "CM_P3_REDIS_TEST", matches = "true")
@SpringBootTest(classes = MileageFlushJobTest.TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "spring.data.redis.host=${CM_P3_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${CM_P3_REDIS_PORT:6379}",
        "spring.data.redis.password=${CM_P3_REDIS_PASSWORD:}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id",
        "cm.telemetry.time.zone=Asia/Shanghai",
        "cm.telemetry.mileage.base-key-prefix=cm:test:mileage:base:",
        "cm.telemetry.mileage.day-key-prefix=cm:test:mileage:day:",
        "cm.telemetry.mileage.max-delta-kilometers=100",
        "cm.telemetry.mileage.retention-days=7",
        "cm.telemetry.mileage.flush-enabled=true",
        "cm.telemetry.mileage.flush-interval-millis=3600000",
        "cm.telemetry.mileage.flush-initial-delay-millis=3600000"
})
class MileageFlushJobTest {

    private static final String SIM_NO = "P3-FLUSH-SIM";

    private static final String PLATE_NO = "测F0001";

    private static final String FIRST_DAY = "20261115";

    private static final String SECOND_DAY = "20261116";

    private static final VehicleBinding BINDING = new VehicleBinding(9101L, 8L, PLATE_NO, 2, 12L);

    @Autowired
    private MileageStore mileageStore;

    @Autowired
    private MileageFlushJob flushJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("重复 flush 命中 UPSERT：仍是一行，且数值不变")
    void should_flush_idempotent() {
        accumulate("2026-11-15T02:00:00Z", 1000L);
        accumulate("2026-11-15T02:10:00Z", 1050L);

        flushJob.flush();
        Map<String, Object> first = singleRow(FIRST_DAY);
        // 清掉落库水位，让第二次 flush 真的再走一次 INSERT ... ON DUPLICATE KEY UPDATE（否则会被水位直接跳过）。
        redisTemplate.opsForHash().delete(dayKey(FIRST_DAY), "flushed");
        flushJob.flush();
        Map<String, Object> second = singleRow(FIRST_DAY);

        assertEquals(1, countRows());
        assertEquals(asDecimal(first.get("id")), asDecimal(second.get("id")));
        assertEquals(0, asDecimal(first.get("mileage")).compareTo(asDecimal(second.get("mileage"))));
        assertEquals(0, asDecimal(first.get("mileage")).compareTo(new BigDecimal("5")));
        assertEquals(0, asDecimal(first.get("mileage1")).compareTo(new BigDecimal("100")));
        assertEquals(0, asDecimal(first.get("mileage2")).compareTo(new BigDecimal("105")));
        assertEquals(0, asDecimal(first.get("mileage2")).subtract(asDecimal(first.get("mileage1")))
                .compareTo(asDecimal(first.get("mileage"))));
    }

    @Test
    @DisplayName("跨天分成两条记录，各自从当天基线起算")
    void should_split_rows_across_days() {
        accumulate("2026-11-15T02:00:00Z", 1000L);
        accumulate("2026-11-15T02:10:00Z", 1050L);
        accumulate("2026-11-16T01:00:00Z", 1100L);

        flushJob.flush();

        assertEquals(2, countRows());
        assertEquals(0, asDecimal(singleRow(FIRST_DAY).get("mileage")).compareTo(new BigDecimal("5")));
        assertEquals(0, asDecimal(singleRow(SECOND_DAY).get("mileage")).compareTo(new BigDecimal("5")));
    }

    @Test
    @DisplayName("后续帧拿不到车牌时不覆盖当天已记录的车牌，里程仍能落库")
    void should_keep_plate_when_binding_lost() {
        accumulate("2026-11-15T02:00:00Z", 1000L);
        mileageStore.accumulate(event("2026-11-15T02:10:00Z", 1050L),
                new VehicleBinding(0L, 0L, "", 0, 0L));

        flushJob.flush();

        assertEquals(1, countRows());
        assertEquals(0, asDecimal(singleRow(FIRST_DAY).get("mileage")).compareTo(new BigDecimal("5")));
    }

    private void accumulate(String deviceTime, long mileageRaw) {
        mileageStore.accumulate(event(deviceTime, mileageRaw), BINDING);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM cm_mileage_statistic WHERE plate_no = ?", PLATE_NO);
        redisTemplate.delete(Set.of(
                "cm:test:mileage:base:" + SIM_NO, dayKey(FIRST_DAY), dayKey(SECOND_DAY)));
    }

    private static String dayKey(String day) {
        return "cm:test:mileage:day:" + SIM_NO + ":" + day;
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cm_mileage_statistic WHERE plate_no = ?", Integer.class, PLATE_NO);
        return count == null ? 0 : count;
    }

    private Map<String, Object> singleRow(String day) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM cm_mileage_statistic WHERE plate_no = ? AND period_descr = ?",
                PLATE_NO, day);
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private static BigDecimal asDecimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private static PositionEvent event(String deviceTime, long mileageRaw) {
        PositionSample sample = new PositionSample(0, 1, 30200000, 120000000, 100, 600, 90,
                mileageRaw, 300, 550, 26, 240);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, "E-" + deviceTime,
                PositionEvent.EVENT_TYPE, PositionEvent.BRANCH_STANDARD, SIM_NO, 1,
                deviceTime, deviceTime, deviceTime, "NORMAL", "gw-test", "trace-1", 0,
                sample, "digest");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.cm.infrastructure.service")
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import({TelemetryConfiguration.class, TelemetryMetrics.class, MileageStore.class,
            MileageFlushJob.class, CmCommandConfiguration.class, CmIdConfiguration.class})
    static class TestApplication {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
