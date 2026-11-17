package com.cm.infrastructure.service.impl;

import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.P2TestApplication;
import com.cm.infrastructure.persistence.entity.CmAlarmProcess;
import com.cm.infrastructure.persistence.entity.CmSystemConfig;
import com.cm.infrastructure.persistence.mapper.CmAlarmProcessMapper;
import com.cm.infrastructure.persistence.mapper.CmSystemConfigMapper;
import com.cm.infrastructure.service.CmAlarmRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 真实 MySQL 上的事务与严格模式验证。默认不跑，设置 CM_P2_DB_TEST=true 并先执行 docs/db/migration 的 V2/V3/V4 后启用。
 *
 * <p>回滚用例不在外层手动开事务：让 process 自己开启 @Transactional 事务，
 * 在第二步审计写入处注入失败，再断言第一步的状态更新也被回滚。
 * 删掉 process 上的 @Transactional，这个用例会失败。
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
class P2TransactionRollbackTest {

    private static final long TEST_ALARM_ID = 990000000000000001L;

    private static final DataScope SCOPE = DataScope.superAdmin(1L);

    @Autowired
    private CmAlarmRecordService alarmRecordService;

    @Autowired
    private CmSystemConfigMapper systemConfigMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_rollback_state_update_when_audit_insert_fails() {
        String eventKey = "P2-ROLLBACK-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO cm_alarm_record "
                        + "(id, event_key, alarm_channel, plate_no, is_processed, create_time, update_time) "
                        + "VALUES (?, ?, 'TERMINAL', 'TEST-0001', 0, NOW(3), NOW(3))",
                TEST_ALARM_ID, eventKey);
        CmAlarmRecordServiceImpl target = AopTestUtils.getTargetObject(alarmRecordService);
        CmAlarmProcessMapper original = (CmAlarmProcessMapper) ReflectionTestUtils
                .getField(target, "alarmProcessMapper");
        CmAlarmProcessMapper failing = Mockito.mock(CmAlarmProcessMapper.class);
        when(failing.insert(any(CmAlarmProcess.class)))
                .thenThrow(new IllegalStateException("强制审计写入失败"));
        try {
            ReflectionTestUtils.setField(target, "alarmProcessMapper", failing);

            assertThrows(IllegalStateException.class,
                    () -> alarmRecordService.process(SCOPE, TEST_ALARM_ID, 1L, "测试处理人", "HANDLED", "回滚验证"));

            Integer auditCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cm_alarm_process WHERE alarm_id = ?", Integer.class, TEST_ALARM_ID);
            Integer processed = jdbcTemplate.queryForObject(
                    "SELECT is_processed FROM cm_alarm_record WHERE id = ?", Integer.class, TEST_ALARM_ID);
            assertEquals(0, auditCount);
            assertEquals(0, processed);
        } finally {
            ReflectionTestUtils.setField(target, "alarmProcessMapper", original);
            jdbcTemplate.update("DELETE FROM cm_alarm_process WHERE alarm_id = ?", TEST_ALARM_ID);
            jdbcTemplate.update("DELETE FROM cm_alarm_record WHERE id = ?", TEST_ALARM_ID);
        }
    }

    /** DDL 的 NOT NULL DEFAULT 必须能在 MySQL 8 严格模式下由应用层直接插入，不依赖装载脚本放宽 sql_mode。 */
    @Test
    void should_insert_entity_under_strict_mode() {
        CmSystemConfig config = new CmSystemConfig();
        config.setCompanyId(0L);
        config.setConfigKey("P2_STRICT_MODE_CHECK");
        config.setConfigValue("1");

        assertEquals(1, systemConfigMapper.insert(config));
        assertNotNull(config.getId());

        jdbcTemplate.update("DELETE FROM cm_system_config WHERE config_key = 'P2_STRICT_MODE_CHECK'");
    }
}
