package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.domain.command.CommandQuery;
import com.cm.domain.command.GovLinkCommand;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.gov809.GovCommandStatus;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmJt809CommandMapper;
import com.cm.infrastructure.service.CmJt809CommandService;
import com.cm.infrastructure.service.CmTerminalCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指令域 SQL 真库验证：命令结果的数据范围/状态/前缀过滤、scope 缺失 fail-closed，
 * 以及 809 链路命令的入队、条件抢占与结果回写幂等。
 * 默认不跑，设置 CM_P4_DB_TEST=true 启用；事务回滚，不向库内留下测试行。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(classes = P4CommandQueryDbTest.TestApplication.class, properties = {
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
class P4CommandQueryDbTest {

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final long PLATFORM_ID = 8992L;

    private static final DataScope DALIAN_SCOPE =
            new DataScope(1L, false, List.of(1L), List.of(DALIAN_DEPARTMENT_ID));

    @Autowired
    private CmTerminalCommandService terminalCommandService;

    @Autowired
    private CmJt809CommandService govCommandService;

    @Autowired
    private CmJt809CommandMapper govCommandMapper;

    @Test
    void should_filter_terminal_commands_by_scope_status_and_prefix() {
        terminalCommandService.save(command("P4CmdDb大连", DALIAN_DEPARTMENT_ID, "SUCCESS"));
        terminalCommandService.save(command("P4CmdDb湖南", HUNAN_DEPARTMENT_ID, "ACCEPTED"));

        IPage<CmTerminalCommand> page = terminalCommandService.pageByScope(DALIAN_SCOPE,
                new CommandQuery("P4CmdDb", null, null, null), 1, 20);

        assertEquals(1, page.getTotal(), "范围内只能看到大连中心的命令");
        assertEquals("P4CmdDb大连", page.getRecords().get(0).getPlateNo());

        assertEquals(0, terminalCommandService.pageByScope(DALIAN_SCOPE,
                new CommandQuery("P4CmdDb", "ACCEPTED", null, null), 1, 20).getTotal());
        assertEquals(1, terminalCommandService.pageByScope(DALIAN_SCOPE,
                new CommandQuery("P4CmdDb", "SUCCESS", null, null), 1, 20).getTotal());
    }

    @Test
    void should_fail_closed_without_scope() {
        assertThrows(CmBusinessException.class, () -> terminalCommandService.pageByScope(null,
                new CommandQuery(null, null, null, null), 1, 20));
    }

    @Test
    void should_claim_link_command_once_and_mark_result() {
        Long id = govCommandService.acceptLinkCommand(PLATFORM_ID, GovLinkCommand.CONNECT_MAIN_LINK, 7L);

        assertTrue(govCommandService.claim(id), "第一次抢占成功");
        assertFalse(govCommandService.claim(id), "已被抢占的命令不能重复执行");
        assertTrue(govCommandService.markResult(id, GovCommandStatus.SENT, "ok"));
        assertFalse(govCommandService.markResult(id, GovCommandStatus.SENT, "ok"),
                "非执行中状态不再回写");

        CmJt809Command row = govCommandMapper.selectById(id);
        assertEquals(GovCommandStatus.SENT, row.getCommandStatus());
        assertEquals(GovLinkCommand.CONNECT_MAIN_LINK, row.getCmd());
        assertEquals("SEND", row.getSource());
    }

    @Test
    void should_reject_unsupported_link_command() {
        assertThrows(CmBusinessException.class,
                () -> govCommandService.acceptLinkCommand(PLATFORM_ID, 0x1200, 7L));
    }

    @Test
    void should_list_only_pending_send_commands() {
        Long sendId = govCommandService.acceptLinkCommand(PLATFORM_ID, GovLinkCommand.LOGOUT_MAIN_LINK, 7L);
        CmJt809Command received = new CmJt809Command();
        received.setPlatformId(PLATFORM_ID);
        received.setSn(1);
        received.setCmd(0x9301);
        received.setSubCmd(0);
        received.setCmdData("");
        received.setDescr("[平台查岗请求]");
        received.setPlateNo("");
        received.setPlateColor(0);
        received.setSimNo("");
        received.setSource("RECEIVE");
        received.setCommandStatus(GovCommandStatus.RECEIVED);
        received.setUserId(0L);
        received.setCompanyId(0L);
        govCommandMapper.insert(received);

        List<CmJt809Command> pending = govCommandService.listPending(20);

        assertTrue(pending.stream().anyMatch(row -> row.getId().equals(sendId)));
        assertTrue(pending.stream().noneMatch(row -> row.getId().equals(received.getId())));
    }

    @Test
    void should_recover_stale_execution() {
        Long id = govCommandService.acceptLinkCommand(PLATFORM_ID, GovLinkCommand.NOTIFY_CLOSE_LINK, 7L);
        assertTrue(govCommandService.claim(id));
        govCommandMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<CmJt809Command>lambdaUpdate()
                .eq(CmJt809Command::getId, id)
                .set(CmJt809Command::getUpdateTime, java.time.LocalDateTime.now().minusMinutes(10)));

        int recovered = govCommandService.recoverStaleExecution(5);

        assertTrue(recovered >= 1);
        CmJt809Command row = govCommandMapper.selectById(id);
        assertEquals(GovCommandStatus.FAILED, row.getCommandStatus());
        assertTrue(row.getDescr().contains("执行超时"));
    }

    private CmTerminalCommand command(String plateNo, long departmentId, String status) {
        CmTerminalCommand command = new CmTerminalCommand();
        command.setCommandId(UUID.randomUUID().toString());
        command.setCompanyId(0L);
        command.setDepartmentId(departmentId);
        command.setVehicleId(1L);
        command.setPlateNo(plateNo);
        command.setSimNo("P4SIM" + UUID.randomUUID().toString().substring(0, 8));
        command.setCommandType(0x8201);
        command.setCommandCode("0x8201");
        command.setCommandData("");
        command.setTerminalSerialNo(0);
        command.setAttemptNo(1);
        command.setCommandStatus(status);
        command.setOperatorId(7L);
        command.setRemark("");
        return command;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import({CmTerminalCommandServiceImpl.class, CmJt809CommandServiceImpl.class})
    static class TestApplication {
    }
} 
