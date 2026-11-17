package com.cm.infrastructure.service.impl;

import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.P2TestApplication;
import com.cm.infrastructure.persistence.mapper.ReportMapper;
import com.cm.infrastructure.service.CmAlarmRecordService;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.ReportQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过真实 Service / Mapper XML 查询新库，与旧库参考查询比对。
 * deploy/p2/parity-*.sql 只验证装载的表内容，本测试验证的是生产查询链路本身：
 * JOIN、过滤、分页计数、数据范围以及 scope 缺失时的 fail-closed 行为。
 *
 * <p>前置：CM_P2_DB_TEST=true、V2/V3/V4 迁移已执行、load-parity-data.sql 已装载，
 * 且 cm_app 对 gps / gps_hisdata 只有 SELECT 权限（见 deploy/init-mysql.sql）。
 */
@EnabledIfEnvironmentVariable(named = "CM_P2_DB_TEST", matches = "true")
@SpringBootTest(classes = P2TestApplication.class, properties = {
        "spring.datasource.url=${CM_P2_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P2_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P2_DB_PASSWORD:CmApp@2026}",
        "spring.session.store-type=none",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted"
})
class P2MapperParityTest {

    private static final DataScope ADMIN_SCOPE = DataScope.superAdmin(1L);

    private static final LocalDateTime START_2018 = LocalDateTime.of(2018, 1, 1, 0, 0);

    private static final LocalDateTime END_2018 = LocalDateTime.of(2018, 12, 31, 23, 59, 59);

    @Autowired
    private CmVehicleService vehicleService;

    @Autowired
    private CmAlarmRecordService alarmRecordService;

    @Autowired
    private ReportQueryService reportQueryService;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_match_legacy_vehicle_count_through_mapper_xml() {
        Long legacy = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gps.vehicle v JOIN gps.department d ON d.depId = v.depId",
                Long.class);

        var page = vehicleService.pageByScope(ADMIN_SCOPE, 1, 10, null, null);

        assertEquals(legacy.longValue(), page.getTotal());
        assertEquals(10, page.getRecords().size());
    }

    @Test
    void should_match_legacy_alarm_count_through_mapper_xml() {
        Long legacy = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM gps.alarmrecord)"
                        + " + (SELECT COUNT(*) FROM gps.hisalarmrecord)"
                        + " + (SELECT COUNT(*) FROM gps.newalarm)",
                Long.class);

        var page = alarmRecordService.pageByScope(ADMIN_SCOPE, null, null, null, null, null, 1, 10);

        assertEquals(legacy.longValue(), page.getTotal());
        assertEquals(10, page.getRecords().size());
    }

    @Test
    void should_match_legacy_department_tree_through_mapper_xml() {
        Long legacy = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM gps.department", Long.class);

        assertEquals(legacy.intValue(), reportQueryService.departments(ADMIN_SCOPE).size());
    }

    @Test
    void should_match_legacy_operation_log_through_mapper_xml() {
        Long legacy = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gps.operationlog"
                        + " WHERE IFNULL(detail, '') <> 'operationLog.action'"
                        + " AND createDate >= ? AND createDate <= ?",
                Long.class, START_2018, END_2018);

        assertEquals(legacy.intValue(),
                reportQueryService.operationLog(ADMIN_SCOPE, null, START_2018, END_2018).size());
    }

    @Test
    void should_apply_department_scope_in_mapper_xml() {
        Long departmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM car_monitoring.cm_department LIMIT 1", Long.class);
        Long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_vehicle v"
                        + " JOIN car_monitoring.cm_department d ON d.id = v.department_id"
                        + " WHERE v.department_id = ? AND v.is_deleted = 0 AND d.is_deleted = 0",
                Long.class, departmentId);
        DataScope limited = new DataScope(1L, false, List.of(0L), List.of(departmentId));

        assertEquals(expected.longValue(), vehicleService.pageByScope(limited, 1, 10, null, null).getTotal());

        DataScope noAccess = new DataScope(1L, false, List.of(0L), List.of(Long.MAX_VALUE));
        assertEquals(0, vehicleService.pageByScope(noAccess, 1, 10, null, null).getTotal());
    }

    @Test
    void should_return_empty_when_scope_is_missing_at_mapper_level() {
        assertTrue(reportMapper.selectOperationLog(null, null, START_2018, END_2018).isEmpty());
        assertTrue(reportMapper.selectDepartments(null).isEmpty());
    }
}
