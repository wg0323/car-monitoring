package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.P3TestApplication;
import com.cm.infrastructure.command.CommandAckEvent;
import com.cm.infrastructure.command.CommandStatus;
import com.cm.infrastructure.command.CommandTimeoutSweeper;
import com.cm.infrastructure.persistence.entity.CmMqDeadLetter;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmMqDeadLetterMapper;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandAckService;
import com.cm.infrastructure.service.CmCommandAttemptService;
import com.cm.infrastructure.service.CmCommandOutboxStateService;
import com.cm.infrastructure.service.CmMqDeadLetterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 上的状态机：条件更新只生效一次，终态不回退，映射缺失落异常记录。
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
class CommandAckStateMachineTest {

    private static final String COMMAND_PREFIX = "P3-CMD-";

    private static final String DEAD_PREFIX = "P3-DLQ-";

    private static final long INCARNATION = 1730000000777L;

    private static final int POSITION_QUERY_ACK = 0x0201;

    private static final int GENERIC_ACK = 0x0001;

    @Autowired
    private CmCommandAckService ackService;

    @Autowired
    private CmCommandAttemptService attemptService;

    @Autowired
    private CmTerminalCommandMapper terminalCommandMapper;

    @Autowired
    private CmMqDeadLetterMapper deadLetterMapper;

    @Autowired
    private CmCommandOutboxStateService outboxStateService;

    @Autowired
    private CmMqDeadLetterService deadLetterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CommandTimeoutSweeper sweeper;

    private String runPrefix;

    private String runSim;

    @BeforeEach
    void setUp() {
        sweeper = new CommandTimeoutSweeper(terminalCommandMapper, outboxStateService);
        runPrefix = COMMAND_PREFIX + UUID.randomUUID();
        runSim = "P3SIM-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM cm_command_ack_exception WHERE sim_no = ?", runSim);
        jdbcTemplate.update("DELETE FROM cm_command_attempt WHERE command_id LIKE ?", COMMAND_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM cm_terminal_command WHERE command_id LIKE ?", COMMAND_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM cm_mq_dead_letter WHERE message_id LIKE ?", DEAD_PREFIX + "%");
    }

    @Test
    @DisplayName("0x0201 业务应答即成功，并记录应答时间")
    void should_advance_to_success_on_position_query_ack() {
        String commandId = runPrefix + "-1";
        insertCommand(commandId, CommandStatus.SENT_TERMINAL, LocalDateTime.now().plusMinutes(5));
        attemptService.register(commandId, runSim, 11, INCARNATION, "gw-1", 0x8201, POSITION_QUERY_ACK);

        ackService.handle(event(11, POSITION_QUERY_ACK, 0));

        CmTerminalCommand reloaded = terminalCommandMapper.selectByCommandId(commandId);
        assertEquals(CommandStatus.SUCCESS, reloaded.getCommandStatus());
        assertNotNull(reloaded.getAckAt());
    }

    @Test
    @DisplayName("重复应答只处理一次，应答时间不被覆盖")
    void should_process_ack_only_once() throws Exception {
        String commandId = runPrefix + "-2";
        insertCommand(commandId, CommandStatus.SENT_TERMINAL, LocalDateTime.now().plusMinutes(5));
        attemptService.register(commandId, runSim, 12, INCARNATION, "gw-1", 0x8201, POSITION_QUERY_ACK);

        ackService.handle(event(12, POSITION_QUERY_ACK, 0));
        LocalDateTime firstAckAt = terminalCommandMapper.selectByCommandId(commandId).getAckAt();
        Thread.sleep(30L);
        ackService.handle(event(12, POSITION_QUERY_ACK, 0));

        CmTerminalCommand reloaded = terminalCommandMapper.selectByCommandId(commandId);
        assertEquals(CommandStatus.SUCCESS, reloaded.getCommandStatus());
        assertEquals(firstAckAt, reloaded.getAckAt());
    }

    @Test
    @DisplayName("终态之后到达的应答不回退状态")
    void should_not_regress_terminal_state_on_late_ack() throws Exception {
        String commandId = runPrefix + "-3";
        insertCommand(commandId, CommandStatus.SENT_TERMINAL, LocalDateTime.now().plusMinutes(5));
        attemptService.register(commandId, runSim, 13, INCARNATION, "gw-1", 0x8201, POSITION_QUERY_ACK);
        ackService.handle(event(13, POSITION_QUERY_ACK, 0));
        LocalDateTime firstAckAt = terminalCommandMapper.selectByCommandId(commandId).getAckAt();

        Thread.sleep(30L);
        ackService.handle(event(13, GENERIC_ACK, 0));

        CmTerminalCommand reloaded = terminalCommandMapper.selectByCommandId(commandId);
        assertEquals(CommandStatus.SUCCESS, reloaded.getCommandStatus());
        assertEquals(firstAckAt, reloaded.getAckAt());
    }

    @Test
    @DisplayName("没有映射的应答只落异常记录，不推进指令")
    void should_record_exception_when_mapping_missing() {
        String commandId = runPrefix + "-4";
        insertCommand(commandId, CommandStatus.SENT_TERMINAL, LocalDateTime.now().plusMinutes(5));

        ackService.handle(event(9999, GENERIC_ACK, 0));

        Integer exceptions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cm_command_ack_exception WHERE sim_no = ? AND reason = 'NO_ATTEMPT_MAPPING'",
                Integer.class, runSim);
        assertEquals(1, exceptions);
        assertEquals(CommandStatus.SENT_TERMINAL,
                terminalCommandMapper.selectByCommandId(commandId).getCommandStatus());
    }

    @Test
    @DisplayName("超过本次尝试超时窗口后置为超时，后续应答不回退")
    void should_mark_timeout_and_keep_it() {
        String commandId = runPrefix + "-5";
        insertCommand(commandId, CommandStatus.SENT_TERMINAL, LocalDateTime.now().minusSeconds(1));
        attemptService.register(commandId, runSim, 15, INCARNATION, "gw-1", 0x8201, POSITION_QUERY_ACK);

        int timedOut = sweeper.markTimeouts();

        assertTrue(timedOut >= 1);
        assertEquals(CommandStatus.TIMEOUT, terminalCommandMapper.selectByCommandId(commandId).getCommandStatus());

        ackService.handle(event(15, POSITION_QUERY_ACK, 0));

        assertEquals(CommandStatus.TIMEOUT, terminalCommandMapper.selectByCommandId(commandId).getCommandStatus());
    }

    @Test
    @DisplayName("死信重放的条件更新只成功一次")
    void should_replay_dead_letter_only_once() {
        CmMqDeadLetter letter = new CmMqDeadLetter();
        letter.setMessageId(DEAD_PREFIX + UUID.randomUUID());
        letter.setSourceQueue("command.downlink.dlq");
        letter.setExchangeName("cm.command");
        letter.setRoutingKey("command.downlink.gw-1");
        letter.setCommandId(runPrefix + "-6");
        letter.setPayload("{}");
        letter.setHeadersJson("");
        letter.setReason("DEAD_LETTER");
        letter.setDeadStatus("PENDING");
        letter.setReplayCount(0);
        letter.setDeleted(false);
        deadLetterMapper.insert(letter);

        assertEquals(1, deadLetterMapper.markReplayed(letter.getId(), LocalDateTime.now()));
        assertEquals(0, deadLetterMapper.markReplayed(letter.getId(), LocalDateTime.now()));
    }

    @Test
    @DisplayName("下行死信从 x-death 恢复原始路由，应答死信不写 commandId")
    void should_restore_original_route_from_x_death() {
        String messageId = DEAD_PREFIX + UUID.randomUUID();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setHeader("x-death", List.of(Map.of("exchange", "cm.command",
                "routing-keys", List.of("command.downlink.gw-1"))));
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        deadLetterService.parkDeadMessage(message, "command.downlink.dlq", "DEAD_LETTER");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT exchange_name, routing_key, command_id FROM cm_mq_dead_letter WHERE message_id = ?",
                messageId);
        assertEquals("cm.command", row.get("exchange_name"));
        assertEquals("command.downlink.gw-1", row.get("routing_key"));
        assertEquals(messageId, row.get("command_id"));
    }

    @Test
    @DisplayName("缺少原始路由的死信拒绝重放，状态仍为待处理")
    void should_refuse_replay_without_original_route() {
        CmMqDeadLetter letter = new CmMqDeadLetter();
        letter.setMessageId(DEAD_PREFIX + UUID.randomUUID());
        letter.setSourceQueue("command.downlink.dlq");
        letter.setExchangeName("");
        letter.setRoutingKey("");
        letter.setPayload("{}");
        letter.setHeadersJson("");
        letter.setReason("DEAD_LETTER");
        letter.setDeadStatus("PENDING");
        letter.setReplayCount(0);
        letter.setDeleted(false);
        deadLetterMapper.insert(letter);

        assertFalse(deadLetterService.replay(letter.getId()));

        String status = jdbcTemplate.queryForObject(
                "SELECT dead_status FROM cm_mq_dead_letter WHERE id = ?", String.class, letter.getId());
        assertEquals("PENDING", status);
    }

    private void insertCommand(String commandId, String status, LocalDateTime expireAt) {
        CmTerminalCommand command = new CmTerminalCommand();
        command.setCommandId(commandId);
        command.setCompanyId(0L);
        command.setDepartmentId(0L);
        command.setVehicleId(1L);
        command.setPlateNo("P3TEST");
        command.setSimNo(runSim);
        command.setCommandType(1);
        command.setCommandCode("0x8201");
        command.setCommandData("");
        command.setTerminalSerialNo(0);
        command.setAttemptNo(0);
        command.setCommandStatus(status);
        command.setExpireAt(expireAt);
        command.setOperatorId(0L);
        command.setRemark("");
        command.setDeleted(false);
        terminalCommandMapper.insert(command);
    }

    private CommandAckEvent event(int terminalSerialNo, int ackMessageId, int ackResult) {
        return new CommandAckEvent(CommandAckEvent.SCHEMA_VERSION, "evt-" + terminalSerialNo,
                CommandAckEvent.EVENT_TYPE, runSim, terminalSerialNo, INCARNATION, "gw-1",
                ackMessageId, ackResult, "7e0001", LocalDateTime.now().toString());
    }
}
