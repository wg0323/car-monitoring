package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CommandAckEvent;
import com.cm.infrastructure.command.CommandStatus;
import com.cm.infrastructure.persistence.entity.CmCommandAckException;
import com.cm.infrastructure.persistence.entity.CmCommandAttempt;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmCommandAckExceptionMapper;
import com.cm.infrastructure.persistence.mapper.CmCommandAttemptMapper;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 终端应答归属与状态推进：映射缺失不猜归属，重复与迟到不动终态。
 */
@ExtendWith(MockitoExtension.class)
class CmCommandAckServiceTest {

    private static final String COMMAND_ID = "cmd-0001";

    private static final String SIM_NO = "1588002223915";

    private static final int SERIAL_NO = 7;

    private static final long INCARNATION = 1730000000123L;

    private static final String GATEWAY_ID = "gw-1";

    @Mock
    private CmCommandAttemptMapper attemptMapper;

    @Mock
    private CmCommandAckExceptionMapper ackExceptionMapper;

    @Mock
    private CmTerminalCommandMapper terminalCommandMapper;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    private CmCommandAckServiceImpl ackService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        ackService = new CmCommandAckServiceImpl(attemptMapper, ackExceptionMapper,
                terminalCommandMapper, meterRegistryProvider);
    }

    @Test
    @DisplayName("找不到尝试映射时只落异常记录，不推进任何指令")
    void should_record_exception_when_mapping_missing() {
        when(attemptMapper.selectByRoute(GATEWAY_ID, SIM_NO, SERIAL_NO, INCARNATION)).thenReturn(null);

        ackService.handle(event(0x0001, 0));

        ArgumentCaptor<CmCommandAckException> captor = ArgumentCaptor.forClass(CmCommandAckException.class);
        verify(ackExceptionMapper).insert(captor.capture());
        assertEquals("NO_ATTEMPT_MAPPING", captor.getValue().getReason());
        assertEquals("", captor.getValue().getCommandId());
        verify(terminalCommandMapper, never()).advanceOnAck(anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("应答消息 ID 与下行不匹配时落异常记录，不按最近指令猜测")
    void should_record_exception_when_ack_message_unexpected() {
        when(attemptMapper.selectByRoute(GATEWAY_ID, SIM_NO, SERIAL_NO, INCARNATION)).thenReturn(attempt(0x0201));

        ackService.handle(event(0x0001, 0));

        ArgumentCaptor<CmCommandAckException> captor = ArgumentCaptor.forClass(CmCommandAckException.class);
        verify(ackExceptionMapper).insert(captor.capture());
        assertEquals("UNEXPECTED_ACK_MESSAGE", captor.getValue().getReason());
        assertEquals(COMMAND_ID, captor.getValue().getCommandId());
        verify(terminalCommandMapper, never()).advanceOnAck(anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("0x0201 位置查询应答直接推进到成功")
    void should_advance_to_success_on_position_query_ack() {
        when(attemptMapper.selectByRoute(GATEWAY_ID, SIM_NO, SERIAL_NO, INCARNATION)).thenReturn(attempt(0x0201));
        when(terminalCommandMapper.advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.SUCCESS),
                any(LocalDateTime.class))).thenReturn(1);

        ackService.handle(event(0x0201, 0));

        verify(terminalCommandMapper).advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.SUCCESS),
                any(LocalDateTime.class));
        verify(ackExceptionMapper, never()).insert(any(CmCommandAckException.class));
    }

    @Test
    @DisplayName("通用应答结果为成功时只推进到终端已应答")
    void should_advance_to_acked_on_generic_ack_success() {
        when(attemptMapper.selectByRoute(GATEWAY_ID, SIM_NO, SERIAL_NO, INCARNATION)).thenReturn(attempt(0x0001));
        when(terminalCommandMapper.advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.ACKED),
                any(LocalDateTime.class))).thenReturn(1);

        ackService.handle(event(0x0001, 0));

        verify(terminalCommandMapper).advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.ACKED),
                any(LocalDateTime.class));
    }

    @Test
    @DisplayName("通用应答结果为失败时推进到失败")
    void should_advance_to_failed_on_generic_ack_failure() {
        when(attemptMapper.selectByRoute(GATEWAY_ID, SIM_NO, SERIAL_NO, INCARNATION)).thenReturn(attempt(0x0001));
        when(terminalCommandMapper.advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.FAILED),
                any(LocalDateTime.class))).thenReturn(1);

        ackService.handle(event(0x0001, 1));

        verify(terminalCommandMapper).advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.FAILED),
                any(LocalDateTime.class));
    }

    @Test
    @DisplayName("重复应答影响行数为 0，既不推进也不写异常")
    void should_ignore_duplicate_ack() {
        when(attemptMapper.selectByRoute(GATEWAY_ID, SIM_NO, SERIAL_NO, INCARNATION)).thenReturn(attempt(0x0001));
        when(terminalCommandMapper.advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.ACKED),
                any(LocalDateTime.class))).thenReturn(0);
        when(terminalCommandMapper.selectByCommandId(COMMAND_ID)).thenReturn(command(CommandStatus.SENT_TERMINAL));

        ackService.handle(event(0x0001, 0));

        verify(terminalCommandMapper).advanceOnAck(eq(COMMAND_ID), eq(CommandStatus.ACKED),
                any(LocalDateTime.class));
        verify(ackExceptionMapper, never()).insert(any(CmCommandAckException.class));
    }

    private CmCommandAttempt attempt(int expectedAckMessageId) {
        CmCommandAttempt attempt = new CmCommandAttempt();
        attempt.setCommandId(COMMAND_ID);
        attempt.setAttemptNo(1);
        attempt.setSimNo(SIM_NO);
        attempt.setTerminalSerialNo(SERIAL_NO);
        attempt.setConnectionIncarnation(INCARNATION);
        attempt.setExpectedAckMessageId(expectedAckMessageId);
        return attempt;
    }

    private CommandAckEvent event(int ackMessageId, int ackResult) {
        return new CommandAckEvent(CommandAckEvent.SCHEMA_VERSION, "evt-1", CommandAckEvent.EVENT_TYPE,
                SIM_NO, SERIAL_NO, INCARNATION, GATEWAY_ID, ackMessageId, ackResult, "7e0001", "now");
    }

    private CmTerminalCommand command(String status) {
        CmTerminalCommand command = new CmTerminalCommand();
        command.setCommandId(COMMAND_ID);
        command.setCommandStatus(status);
        return command;
    }
}
