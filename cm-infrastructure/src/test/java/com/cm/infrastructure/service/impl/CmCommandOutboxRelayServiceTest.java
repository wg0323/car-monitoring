package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CmCommandMqProperties;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.CommandStatus;
import com.cm.infrastructure.command.TerminalRoute;
import com.cm.infrastructure.command.TerminalRouteRegistry;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandOutboxLeaseService;
import com.cm.infrastructure.service.CmCommandOutboxStateService;
import com.cm.infrastructure.service.CmCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 转发：路由决定 routingKey、指令终态不发布、发布异常回退重试、抢不到就跳过。
 */
@ExtendWith(MockitoExtension.class)
class CmCommandOutboxRelayServiceTest {

    private static final String COMMAND_ID = "cmd-0001";

    private static final String SIM_NO = "1588002223915";

    private static final String GATEWAY_ID = "gw-1";

    @Mock
    private CmCommandOutboxLeaseService leaseService;

    @Mock
    private CmCommandOutboxStateService outboxStateService;

    @Mock
    private CmTerminalCommandMapper terminalCommandMapper;

    @Mock
    private TerminalRouteRegistry routeRegistry;

    @Mock
    private CmCommandPublisher publisher;

    private CmCommandOutboxRelayServiceImpl relayService;

    @BeforeEach
    void setUp() {
        relayService = new CmCommandOutboxRelayServiceImpl(leaseService, outboxStateService,
                terminalCommandMapper, routeRegistry, publisher,
                new CmCommandProperties(), new CmCommandMqProperties());
    }

    @Test
    @DisplayName("按 Redis 路由投递到 command.downlink.{gatewayId}，消息体带同一 commandId")
    void should_publish_to_gateway_queue_when_route_exists() {
        CmCommandOutbox row = outboxRow();
        when(leaseService.claim(anyString())).thenReturn(List.of(row));
        when(terminalCommandMapper.selectByCommandId(COMMAND_ID)).thenReturn(command(CommandStatus.ACCEPTED));
        when(routeRegistry.find(SIM_NO)).thenReturn(Optional.of(new TerminalRoute(GATEWAY_ID, 9L)));
        when(outboxStateService.markPublished(eq(row), eq("cm.command"), eq("command.downlink.gw-1"),
                anyString())).thenReturn(OptionalInt.of(1));

        int published = relayService.relayOnce();

        assertEquals(1, published);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(publisher).publishDownlink(eq(COMMAND_ID), eq(1), eq("cm.command"),
                eq("command.downlink.gw-1"), payload.capture());
        assertTrue(payload.getValue().contains("\"commandId\":\"" + COMMAND_ID + "\""));
        assertTrue(payload.getValue().contains("\"gatewayId\":\"" + GATEWAY_ID + "\""));
    }

    @Test
    @DisplayName("没有路由时不发布，尝试次数递增后退避重试")
    void should_defer_when_route_missing() {
        CmCommandOutbox row = outboxRow();
        when(leaseService.claim(anyString())).thenReturn(List.of(row));
        when(terminalCommandMapper.selectByCommandId(COMMAND_ID)).thenReturn(command(CommandStatus.ACCEPTED));
        when(routeRegistry.find(SIM_NO)).thenReturn(Optional.empty());

        int published = relayService.relayOnce();

        assertEquals(0, published);
        verify(outboxStateService).defer(row, "NO_ROUTE");
        verify(publisher, never()).publishDownlink(anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("指令已进入终态时 Outbox 置 SKIPPED，不再发布")
    void should_skip_when_command_already_terminal() {
        CmCommandOutbox row = outboxRow();
        when(leaseService.claim(anyString())).thenReturn(List.of(row));
        when(terminalCommandMapper.selectByCommandId(COMMAND_ID)).thenReturn(command(CommandStatus.SUCCESS));

        int published = relayService.relayOnce();

        assertEquals(0, published);
        verify(outboxStateService).skip(row);
        verify(publisher, never()).publishDownlink(anyString(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("发布调用抛异常时回到重试，commandId 不变")
    void should_fail_retry_when_publish_throws() {
        CmCommandOutbox row = outboxRow();
        when(leaseService.claim(anyString())).thenReturn(List.of(row));
        when(terminalCommandMapper.selectByCommandId(COMMAND_ID)).thenReturn(command(CommandStatus.ACCEPTED));
        when(routeRegistry.find(SIM_NO)).thenReturn(Optional.of(new TerminalRoute(GATEWAY_ID, 9L)));
        when(outboxStateService.markPublished(any(), anyString(), anyString(), anyString()))
                .thenReturn(OptionalInt.of(1));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publishDownlink(anyString(), anyInt(), anyString(), anyString(), anyString());

        int published = relayService.relayOnce();

        assertEquals(0, published);
        verify(outboxStateService).fail(COMMAND_ID, 1, "broker down");
    }

    @Test
    @DisplayName("被其他实例抢走时放弃本次记录，不重复发布")
    void should_ignore_when_claim_lost() {
        CmCommandOutbox row = outboxRow();
        when(leaseService.claim(anyString())).thenReturn(List.of(row));
        when(terminalCommandMapper.selectByCommandId(COMMAND_ID)).thenReturn(command(CommandStatus.ACCEPTED));
        when(routeRegistry.find(SIM_NO)).thenReturn(Optional.of(new TerminalRoute(GATEWAY_ID, 9L)));
        when(outboxStateService.markPublished(any(), anyString(), anyString(), anyString()))
                .thenReturn(OptionalInt.empty());

        int published = relayService.relayOnce();

        assertEquals(0, published);
        verify(publisher, never()).publishDownlink(anyString(), anyInt(), anyString(), anyString(),
                anyString());
    }

    private CmCommandOutbox outboxRow() {
        CmCommandOutbox row = new CmCommandOutbox();
        row.setId(1001L);
        row.setCommandId(COMMAND_ID);
        row.setPublishStatus("PENDING");
        row.setRetryCount(0);
        row.setPayload(COMMAND_ID);
        return row;
    }

    private CmTerminalCommand command(String status) {
        CmTerminalCommand command = new CmTerminalCommand();
        command.setCommandId(COMMAND_ID);
        command.setSimNo(SIM_NO);
        command.setCommandType(1);
        command.setCommandCode("0x8201");
        command.setCommandStatus(status);
        return command;
    }
}
