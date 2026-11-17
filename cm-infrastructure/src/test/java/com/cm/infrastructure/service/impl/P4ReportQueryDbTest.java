package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.domain.report.AlarmReportQuery;
import com.cm.domain.report.OnlineRecordQuery;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.persistence.row.OnlineRecordRow;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;
import com.cm.infrastructure.service.ReportQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 报表 SQL 真库对账（TC-DAT-02）：页面 total 与导出全量行数必须相等。
 * 默认不跑，设置 CM_P4_DB_TEST=true 启用；只读查询，不写数据。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(classes = P4ReportQueryDbTest.TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
class P4ReportQueryDbTest {

    private static final DataScope SCOPE = DataScope.superAdmin(1L);

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);

    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    @Autowired
    private ReportQueryService reportQueryService;

    @Test
    void alarm_total_should_equal_export_rows() {
        AlarmReportQuery query = new AlarmReportQuery(null, null, null, null, null, START, END);

        IPage<AlarmRow> page = reportQueryService.alarmPage(SCOPE, query, 1, 20);
        List<AlarmRow> rows = reportQueryService.alarmRows(SCOPE, query, QueryLimits.MAX_EXPORT_ROWS);

        assertEquals(page.getTotal(), rows.size(), "报警页面 total 必须等于导出行数");
    }

    @Test
    void parking_total_should_equal_export_rows() {
        AlarmReportQuery query = new AlarmReportQuery("PLATFORM", "Parking", null, null, null, START, END);

        IPage<AlarmRow> page = reportQueryService.alarmPage(SCOPE, query, 1, 20);
        List<AlarmRow> rows = reportQueryService.alarmRows(SCOPE, query, QueryLimits.MAX_EXPORT_ROWS);

        assertEquals(page.getTotal(), rows.size(), "停车页面 total 必须等于导出行数");
    }

    @Test
    void online_record_total_should_equal_export_rows() {
        OnlineRecordQuery query = new OnlineRecordQuery(null, null, null, START, END);

        IPage<OnlineRecordRow> page = reportQueryService.onlineRecordPage(SCOPE, query, 1, 20);
        List<OnlineRecordRow> rows = reportQueryService.onlineRecordRows(SCOPE, query, QueryLimits.MAX_EXPORT_ROWS);

        assertEquals(page.getTotal(), rows.size(), "上下线页面 total 必须等于导出行数");
    }

    @Test
    void online_vehicle_candidates_should_query_without_error() {
        List<VehicleOnlineReportRow> rows = reportQueryService.onlineVehicleCandidates(SCOPE, null, null, null);

        assertNotNull(rows);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import(ReportQueryServiceImpl.class)
    static class TestApplication {
    }
}

