package com.cm.infrastructure.service.impl;

import com.cm.domain.registration.RegistrationVerdict;
import com.cm.infrastructure.P3TestApplication;
import com.cm.infrastructure.service.TerminalRegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 注册判定：SIM 是身份锚点，车牌与颜色必须与该 SIM 登记的车一致，
 * 终端则看车辆绑定的终端号。用本次用例自建数据，不依赖 P2 装载的对照数据。
 * 默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = P3TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
class TerminalRegistrationServiceTest {

    private static final String PREFIX = "P3REG";

    private static final int PLATE_COLOR = 2;

    private static final long VEHICLE_ID = 980001L;

    private static final long TERMINAL_ID = 980002L;

    private static final long OTHER_TERMINAL_ID = 980003L;

    @Autowired
    private TerminalRegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String plateNo;

    private String simNo;

    private String termNo;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        plateNo = PREFIX + suffix;
        simNo = PREFIX + "S" + suffix;
        termNo = PREFIX + "T" + suffix;
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("SIM、车牌、颜色与绑定终端全部匹配时注册成功")
    void should_accept_when_all_match() {
        insertTerminal(TERMINAL_ID, termNo, "");
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, simNo, TERMINAL_ID);

        assertEquals(RegistrationVerdict.SUCCESS,
                registrationService.evaluate(plateNo, PLATE_COLOR, simNo, termNo));
    }

    @Test
    @DisplayName("报文车牌与该 SIM 登记的车不一致时按无该车辆处理")
    void should_reject_when_plate_mismatch() {
        insertTerminal(TERMINAL_ID, termNo, "");
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, simNo, TERMINAL_ID);

        assertEquals(RegistrationVerdict.VEHICLE_NOT_FOUND,
                registrationService.evaluate(plateNo + "X", PLATE_COLOR, simNo, termNo));
    }

    @Test
    @DisplayName("车牌颜色不一致时按无该车辆处理")
    void should_reject_when_plate_color_mismatch() {
        insertTerminal(TERMINAL_ID, termNo, "");
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, simNo, TERMINAL_ID);

        assertEquals(RegistrationVerdict.VEHICLE_NOT_FOUND,
                registrationService.evaluate(plateNo, PLATE_COLOR + 1, simNo, termNo));
    }

    @Test
    @DisplayName("SIM 在库里没有对应车辆时按无该车辆处理，不认车牌")
    void should_reject_when_sim_unregistered() {
        insertTerminal(TERMINAL_ID, termNo, "");
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, "OTHER-SIM", TERMINAL_ID);

        assertEquals(RegistrationVerdict.VEHICLE_NOT_FOUND,
                registrationService.evaluate(plateNo, PLATE_COLOR, simNo, termNo));
    }

    @Test
    @DisplayName("车辆已绑终端但报文终端号不匹配时按无该终端处理")
    void should_reject_when_bound_terminal_mismatch() {
        insertTerminal(TERMINAL_ID, termNo, "");
        insertTerminal(OTHER_TERMINAL_ID, PREFIX + "OTHER", "");
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, simNo, TERMINAL_ID);

        assertEquals(RegistrationVerdict.TERMINAL_NOT_FOUND,
                registrationService.evaluate(plateNo, PLATE_COLOR, simNo, PREFIX + "OTHER"));
    }

    @Test
    @DisplayName("车辆未绑终端时退回旧口径：库里存在该终端号即放行")
    void should_accept_when_vehicle_unbound_and_terminal_exists() {
        insertTerminal(TERMINAL_ID, termNo, "");
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, simNo, 0L);

        assertEquals(RegistrationVerdict.SUCCESS,
                registrationService.evaluate(plateNo, PLATE_COLOR, simNo, termNo));
    }

    @Test
    @DisplayName("车辆未绑终端且终端号不存在时按无该终端处理")
    void should_reject_when_terminal_missing() {
        insertVehicle(VEHICLE_ID, plateNo, PLATE_COLOR, simNo, 0L);

        assertEquals(RegistrationVerdict.TERMINAL_NOT_FOUND,
                registrationService.evaluate(plateNo, PLATE_COLOR, simNo, termNo));
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM cm_vehicle WHERE plate_no LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM cm_vehicle WHERE sim_no LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM cm_terminal WHERE term_no LIKE ?", PREFIX + "%");
    }

    private void insertVehicle(long id, String plate, int color, String sim, long terminalId) {
        jdbcTemplate.update("INSERT INTO cm_vehicle (id, company_id, department_id, plate_no, plate_color,"
                + " sim_no, terminal_id, is_deleted, create_time, update_time)"
                + " VALUES (?, 0, 0, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                id, plate, color, sim, terminalId);
    }

    private void insertTerminal(long id, String termNo, String simNo) {
        jdbcTemplate.update("INSERT INTO cm_terminal (id, company_id, department_id, term_no, sim_no,"
                + " is_bound, is_deleted, create_time, update_time)"
                + " VALUES (?, 0, 0, ?, ?, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                id, termNo, simNo);
    }
}
