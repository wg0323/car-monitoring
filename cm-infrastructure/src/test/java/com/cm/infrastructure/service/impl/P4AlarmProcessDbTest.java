package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmAlarmProcess;
import com.cm.infrastructure.persistence.entity.CmAlarmRecord;
import com.cm.infrastructure.persistence.mapper.CmAlarmProcessMapper;
import com.cm.infrastructure.persistence.mapper.CmAlarmRecordMapper;
import com.cm.infrastructure.service.CmAlarmRecordService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-DAT-03 报警处理落库的真库验证：处理状态、处理人、处理时间与备注是否真的写进报警行，
 * 审计行是否同步落库，重复处理与越界处理是否都被挡住。
 * 默认不跑，设置 CM_P4_DB_TEST=true 启用；事务回滚，不留造出来的数据。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(classes = P4AlarmProcessDbTest.TestApplication.class, properties = {
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
class P4AlarmProcessDbTest {

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final long PROCESSOR_ID = 100000000000120201L;

    private static final String PROCESSOR_NAME = "系统管理员";

    private static final String PROCESS_RESULT = "PROCESSED";

    private static final DataScope DALIAN_SCOPE =
            new DataScope(1L, false, List.of(1L), List.of(DALIAN_DEPARTMENT_ID));

    @Autowired
    private CmAlarmRecordService alarmRecordService;

    @Autowired
    private CmAlarmRecordMapper alarmRecordMapper;

    @Autowired
    private CmAlarmProcessMapper alarmProcessMapper;

    @Test
    void should_write_processed_fields_into_alarm_row_and_audit() {
        CmAlarmRecord alarm = alarm(DALIAN_DEPARTMENT_ID);
        alarmRecordMapper.insert(alarm);

        alarmRecordService.process(DALIAN_SCOPE, alarm.getId(), PROCESSOR_ID, PROCESSOR_NAME,
                PROCESS_RESULT, "已电话核实");

        CmAlarmRecord processed = alarmRecordMapper.selectById(alarm.getId());
        assertTrue(Boolean.TRUE.equals(processed.getProcessed()), "is_processed 必须置 1");
        assertNotNull(processed.getProcessedTime(), "processed_time 必须写入");
        assertEquals(PROCESSOR_ID, processed.getProcessedUserId());
        assertEquals(PROCESSOR_NAME, processed.getProcessedUserName());
        assertEquals("已电话核实", processed.getRemark(), "备注要落在报警行上，不能只写审计表");

        List<CmAlarmProcess> audits = alarmProcessMapper.selectList(
                new LambdaQueryWrapper<CmAlarmProcess>().eq(CmAlarmProcess::getAlarmId, alarm.getId()));
        assertEquals(1, audits.size(), "一次处理只写一行审计");
        CmAlarmProcess audit = audits.get(0);
        assertEquals(PROCESSOR_ID, audit.getProcessorId());
        assertEquals(PROCESSOR_NAME, audit.getProcessorName());
        assertEquals(PROCESS_RESULT, audit.getProcessResult());
        assertEquals("已电话核实", audit.getProcessRemark());
        assertNotNull(audit.getProcessTime());
    }

    @Test
    void should_reject_second_process_and_keep_single_audit() {
        CmAlarmRecord alarm = alarm(DALIAN_DEPARTMENT_ID);
        alarmRecordMapper.insert(alarm);
        alarmRecordService.process(DALIAN_SCOPE, alarm.getId(), PROCESSOR_ID, PROCESSOR_NAME,
                PROCESS_RESULT, "首次处理");

        assertThrows(CmBusinessException.class, () -> alarmRecordService.process(DALIAN_SCOPE, alarm.getId(),
                PROCESSOR_ID, PROCESSOR_NAME, PROCESS_RESULT, "重复处理"));

        List<CmAlarmProcess> audits = alarmProcessMapper.selectList(
                new LambdaQueryWrapper<CmAlarmProcess>().eq(CmAlarmProcess::getAlarmId, alarm.getId()));
        assertEquals(1, audits.size(), "重复处理不能追加审计");
        assertEquals("首次处理", alarmRecordMapper.selectById(alarm.getId()).getRemark(),
                "重复处理不能覆盖首次备注");
    }

    @Test
    void should_reject_process_out_of_scope() {
        CmAlarmRecord alarm = alarm(HUNAN_DEPARTMENT_ID);
        alarmRecordMapper.insert(alarm);

        assertThrows(CmBusinessException.class, () -> alarmRecordService.process(DALIAN_SCOPE, alarm.getId(),
                PROCESSOR_ID, PROCESSOR_NAME, PROCESS_RESULT, "越界处理"));

        assertFalse(Boolean.TRUE.equals(alarmRecordMapper.selectById(alarm.getId()).getProcessed()),
                "范围外的报警不能被改状态");
    }

    private CmAlarmRecord alarm(long departmentId) {
        CmAlarmRecord record = new CmAlarmRecord();
        record.setEventKey("P4-ALM-PROC-" + UUID.randomUUID());
        record.setAlarmChannel("TERMINAL");
        record.setCompanyId(0L);
        record.setDepartmentId(departmentId);
        record.setVehicleId(42L);
        record.setPlateNo("辽B00001");
        record.setSimNo("15880022239");
        record.setAlarmSource("terminal_alarm");
        record.setAlarmType("1");
        record.setDescription("超速");
        record.setStartTime(LocalDateTime.now().minusMinutes(3));
        record.setLatitude(new BigDecimal("38.914000"));
        record.setLongitude(new BigDecimal("121.614000"));
        record.setVelocity(new BigDecimal("88.00"));
        record.setAlarmStatus("New");
        record.setRemark("");
        return record;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import(CmAlarmRecordServiceImpl.class)
    static class TestApplication {
    }
}
