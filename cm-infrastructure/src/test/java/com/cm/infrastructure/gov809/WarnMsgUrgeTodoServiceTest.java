package com.cm.infrastructure.gov809;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

/**
 * 真实 MySQL 上的督办幂等：同一督办重发只落一行，应答只推进一次。
 * 默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = Gov809TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
class WarnMsgUrgeTodoServiceTest {

    private static final long PLATFORM_ID = 990802L;

    private static final long SUPERVISE_ID = 2147483646L;

    @Autowired
    private WarnMsgUrgeTodoService urgeTodoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM cm_warn_msg_urge_todo_req WHERE platform_id = ?", PLATFORM_ID);
    }

    @Test
    @DisplayName("同一督办重发只落一行，可反查到车牌与督办人")
    void should_record_supervision_once() {
        assertTrue(urgeTodoService.record(row()));
        assertFalse(urgeTodoService.record(row()));

        CmWarnMsgUrgeTodoReq saved = urgeTodoService.find(PLATFORM_ID, SUPERVISE_ID);
        assertNotNull(saved);
        assertEquals("测A00001", saved.getPlateNo());
        assertEquals("张三", saved.getSupervisor());
        assertEquals(0, saved.getAckResult());
    }

    @Test
    @DisplayName("应答只能推进一次，重复应答不改时间")
    void should_acknowledge_only_once() {
        urgeTodoService.record(row());

        assertTrue(urgeTodoService.acknowledge(PLATFORM_ID, SUPERVISE_ID, 1));
        assertFalse(urgeTodoService.acknowledge(PLATFORM_ID, SUPERVISE_ID, 2));

        CmWarnMsgUrgeTodoReq saved = urgeTodoService.find(PLATFORM_ID, SUPERVISE_ID);
        assertEquals(1, saved.getAckResult());
        assertNotNull(saved.getAckTime());
    }

    private CmWarnMsgUrgeTodoReq row() {
        CmWarnMsgUrgeTodoReq row = new CmWarnMsgUrgeTodoReq();
        row.setPlatformId(PLATFORM_ID);
        row.setSuperviseId(SUPERVISE_ID);
        row.setPlateNo("测A00001");
        row.setPlateColor(2);
        row.setWarnSrc(1);
        row.setWarnType(2);
        row.setWarnTime(LocalDateTime.of(2026, 11, 15, 10, 0));
        row.setSupervisionEndTime(LocalDateTime.of(2026, 11, 15, 12, 0));
        row.setSupervisionLevel(1);
        row.setSupervisor("张三");
        row.setSupervisorTel("13800000000");
        row.setSupervisorEmail("a@b.com");
        row.setVehicleId(0L);
        row.setCompanyId(0L);
        return row;
    }
}

