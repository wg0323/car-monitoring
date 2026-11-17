package com.cm.infrastructure.service.impl;

import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmAlarmRecord;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import com.cm.infrastructure.persistence.mapper.CmAlarmRecordMapper;
import com.cm.infrastructure.persistence.mapper.CmWarnMsgUrgeTodoReqMapper;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.service.CmAlarmRecordService;
import com.cm.infrastructure.service.UrgeTodoQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报警通知与 809 督办的真库验证：增量查询的 sinceId 边界、数据范围过滤、
 * 督办应答状态与车牌前缀过滤。默认不跑，设置 CM_P4_DB_TEST=true 启用；事务回滚。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(classes = P4AlarmUrgeDbTest.TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
@Transactional
class P4AlarmUrgeDbTest {

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final long PLATFORM_ID = 8992L;

    private static final DataScope DALIAN_SCOPE =
            new DataScope(1L, false, List.of(1L), List.of(DALIAN_DEPARTMENT_ID));

    private static final LocalDateTime BASE_TIME = LocalDateTime.now().minusMinutes(3);

    @Autowired
    private CmAlarmRecordService alarmRecordService;

    @Autowired
    private CmAlarmRecordMapper alarmRecordMapper;

    @Autowired
    private UrgeTodoQueryService urgeTodoQueryService;

    @Autowired
    private CmWarnMsgUrgeTodoReqMapper urgeTodoMapper;

    @Test
    void should_filter_new_alarms_by_scope_and_since_id() {
        CmAlarmRecord dalian = alarm(DALIAN_DEPARTMENT_ID, "大连");
        alarmRecordMapper.insert(dalian);
        CmAlarmRecord hunan = alarm(HUNAN_DEPARTMENT_ID, "湖南");
        alarmRecordMapper.insert(hunan);

        LocalDateTime windowStart = BASE_TIME.minusMinutes(10);
        List<AlarmRow> baseline = alarmRecordService.listNewAlarms(DALIAN_SCOPE, 0L, windowStart, 50);

        assertEquals(1, baseline.size(), "只能看到大连部门的报警");
        AlarmRow row = baseline.get(0);
        assertEquals("辽B00001", row.getPlateNo());
        assertEquals("15880022239", row.getSimNo());
        assertEquals(new BigDecimal("38.914000"), row.getLatitude());
        assertNotNull(row.getId(), "增量查询必须带回主键，页面用它作为 sinceId");

        List<AlarmRow> afterSince = alarmRecordService.listNewAlarms(DALIAN_SCOPE, row.getId(), windowStart, 50);
        assertTrue(afterSince.isEmpty(), "sinceId 之后不应重复返回同一条报警");
    }

    @Test
    void should_exclude_alarms_before_lookback_window() {
        CmAlarmRecord dalian = alarm(DALIAN_DEPARTMENT_ID, "大连");
        alarmRecordMapper.insert(dalian);

        List<AlarmRow> rows = alarmRecordService.listNewAlarms(DALIAN_SCOPE, 0L,
                LocalDateTime.now().minusMinutes(1), 50);

        assertTrue(rows.stream().noneMatch(row -> row.getId().equals(dalian.getId())),
                "时间窗之前的报警不参与弹窗轮询");
    }

    @Test
    void should_page_urge_todos_by_ack_state_and_prefix() {
        String prefix = uniquePlatePrefix();
        CmWarnMsgUrgeTodoReq pending = urgeTodo(prefix + "0", false);
        urgeTodoMapper.insert(pending);
        CmWarnMsgUrgeTodoReq acknowledged = urgeTodo(prefix + "1", true);
        acknowledged.setAckTime(LocalDateTime.now());
        urgeTodoMapper.insert(acknowledged);

        assertEquals(1, urgeTodoQueryService.pageByQuery(prefix, false, null, null, 1, 10).getTotal());
        assertEquals(1, urgeTodoQueryService.pageByQuery(prefix, true, null, null, 1, 10).getTotal());
        assertEquals(2, urgeTodoQueryService.pageByQuery(prefix, null, null, null, 1, 10).getTotal());

        CmWarnMsgUrgeTodoReq loaded = urgeTodoQueryService.load(pending.getId());
        assertNotNull(loaded);
        assertEquals(Integer.valueOf(1), loaded.getWarnSrc());
        assertEquals(pending.getSuperviseId(), loaded.getSuperviseId());
    }

    @Test
    void should_only_notify_unacknowledged_urge_todos_after_since_id() {
        String prefix = uniquePlatePrefix();
        CmWarnMsgUrgeTodoReq pending = urgeTodo(prefix + "2", false);
        urgeTodoMapper.insert(pending);
        CmWarnMsgUrgeTodoReq acknowledged = urgeTodo(prefix + "3", true);
        acknowledged.setAckTime(LocalDateTime.now());
        urgeTodoMapper.insert(acknowledged);

        List<CmWarnMsgUrgeTodoReq> first = urgeTodoQueryService.listNewUrgeTodos(0L,
                LocalDateTime.now().minusMinutes(5), 20);
        assertTrue(first.stream().anyMatch(row -> row.getId().equals(pending.getId())),
                "未应答督办应进入弹窗轮询");
        assertTrue(first.stream().noneMatch(row -> row.getId().equals(acknowledged.getId())),
                "已应答督办不再弹窗");

        List<CmWarnMsgUrgeTodoReq> afterSince = urgeTodoQueryService.listNewUrgeTodos(pending.getId(),
                LocalDateTime.now().minusMinutes(5), 20);
        assertTrue(afterSince.stream().noneMatch(row -> row.getId().equals(pending.getId())),
                "sinceId 之后不应重复返回同一条督办");
    }

    private CmAlarmRecord alarm(long departmentId, String marker) {
        CmAlarmRecord record = new CmAlarmRecord();
        record.setEventKey("P4-ALM-" + marker + "-" + UUID.randomUUID());
        record.setAlarmChannel("TERMINAL");
        record.setCompanyId(0L);
        record.setDepartmentId(departmentId);
        record.setVehicleId(42L);
        record.setPlateNo("辽B00001");
        record.setSimNo("15880022239");
        record.setAlarmSource("terminal_alarm");
        record.setAlarmType("1");
        record.setDescription("超速");
        record.setStartTime(BASE_TIME);
        record.setLatitude(new BigDecimal("38.914000"));
        record.setLongitude(new BigDecimal("121.614000"));
        record.setMileage1(new BigDecimal("100.00"));
        record.setGas1(new BigDecimal("50.00"));
        record.setVelocity(new BigDecimal("88.00"));
        record.setAlarmStatus("New");
        return record;
    }

    private CmWarnMsgUrgeTodoReq urgeTodo(String plateNo, boolean acknowledged) {
        CmWarnMsgUrgeTodoReq row = new CmWarnMsgUrgeTodoReq();
        row.setPlatformId(PLATFORM_ID);
        row.setSuperviseId(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
        row.setPlateNo(plateNo);
        row.setPlateColor(1);
        row.setWarnSrc(1);
        row.setWarnType(1);
        row.setWarnTime(BASE_TIME);
        row.setSupervisionEndTime(BASE_TIME.plusHours(4));
        row.setSupervisionLevel(0);
        row.setSupervisor("张三");
        row.setSupervisorTel("13800000000");
        row.setSupervisorEmail("zhangsan@example.com");
        row.setVehicleId(0L);
        row.setAckResult(acknowledged ? 1 : 0);
        row.setCompanyId(0L);
        return row;
    }

    /** 每次用不同车牌前缀，避免与库内既有督办或其它用例的记录串到一起。 */
    private String uniquePlatePrefix() {
        return "测" + UUID.randomUUID().toString().substring(0, 6);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import({CmAlarmRecordServiceImpl.class, UrgeTodoQueryServiceImpl.class})
    static class TestApplication {
    }
}
