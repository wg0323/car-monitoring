package com.cm.integration.command;

import com.cm.domain.command.GovLinkCommand;
import com.cm.infrastructure.gov809.GovCommandStatus;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.service.CmJt809CommandService;
import com.cm.integration.config.Gov809Properties;
import com.cm.integration.link.Gov809LinkManager;
import com.cm.integration.link.Jt809UpstreamClient;
import com.cm.protocol.jt809.Jt809Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Gov809LinkCommandExecutorTest {

    @Mock
    private CmJt809CommandService commandService;

    @Mock
    private Jt809UpstreamClient upstreamClient;

    @Mock
    private Gov809LinkManager linkManager;

    @Mock
    private Gov809CommandService govCommandService;

    private Gov809LinkCommandExecutor executor;

    private Gov809Properties properties;

    @BeforeEach
    void setUp() {
        properties = new Gov809Properties();
        executor = new Gov809LinkCommandExecutor(commandService, upstreamClient, linkManager, properties,
                govCommandService);
    }

    @Test
    @DisplayName("0x1001 调主链路连接并置发送成功")
    void should_request_main_link() {
        when(commandService.listPending(20)).thenReturn(List.of(row(1L, GovLinkCommand.CONNECT_MAIN_LINK)));
        when(commandService.claim(1L)).thenReturn(true);
        when(upstreamClient.requestMainLink()).thenReturn(true);

        executor.runOnce();

        verify(upstreamClient).requestMainLink();
        verify(commandService).markResult(1L, GovCommandStatus.SENT,
                GovLinkCommand.describe(GovLinkCommand.CONNECT_MAIN_LINK) + "：已发起连接，登录结果见链路状态");
    }

    @Test
    @DisplayName("0x1003 调主链路注销")
    void should_request_logout() {
        when(commandService.listPending(20)).thenReturn(List.of(row(2L, GovLinkCommand.LOGOUT_MAIN_LINK)));
        when(commandService.claim(2L)).thenReturn(true);
        when(upstreamClient.requestLogout()).thenReturn(true);

        executor.runOnce();

        verify(upstreamClient).requestLogout();
        verify(commandService).markResult(2L, GovCommandStatus.SENT,
                GovLinkCommand.describe(GovLinkCommand.LOGOUT_MAIN_LINK));
    }

    @Test
    @DisplayName("0x1007 与 0x1008 只从从链路发送")
    void should_send_link_notifications_from_sub_link() {
        when(commandService.listPending(20)).thenReturn(List.of(
                row(3L, GovLinkCommand.NOTIFY_MAIN_LINK_DISCONNECTED),
                row(4L, GovLinkCommand.NOTIFY_CLOSE_LINK)));
        when(commandService.claim(3L)).thenReturn(true);
        when(commandService.claim(4L)).thenReturn(true);
        when(linkManager.sendFromSubLink(anyInt(), any(byte[].class))).thenReturn(true);

        executor.runOnce();

        verify(linkManager).sendFromSubLink(eq(GovLinkCommand.NOTIFY_MAIN_LINK_DISCONNECTED),
                aryEq(new byte[0]));
        verify(linkManager).sendFromSubLink(eq(GovLinkCommand.NOTIFY_CLOSE_LINK), aryEq(new byte[0]));
        verifyNoInteractions(upstreamClient);
    }

    @Test
    @DisplayName("抢占失败的命令不执行也不回写")
    void should_skip_when_claim_fails() {
        when(commandService.listPending(20)).thenReturn(List.of(row(5L, GovLinkCommand.CONNECT_MAIN_LINK)));
        when(commandService.claim(5L)).thenReturn(false);

        executor.runOnce();

        verifyNoInteractions(upstreamClient);
        verify(commandService, never()).markResult(anyLong(), any(), any());
    }

    @Test
    @DisplayName("链路不可用时置发送失败并带原因")
    void should_mark_failed_when_link_unavailable() {
        when(commandService.listPending(20)).thenReturn(List.of(row(6L, GovLinkCommand.NOTIFY_CLOSE_LINK)));
        when(commandService.claim(6L)).thenReturn(true);
        when(linkManager.sendFromSubLink(anyInt(), any(byte[].class))).thenReturn(false);

        executor.runOnce();

        verify(commandService).markResult(6L, GovCommandStatus.FAILED,
                GovLinkCommand.describe(GovLinkCommand.NOTIFY_CLOSE_LINK) + "：链路不可用");
    }

    @Test
    @DisplayName("0x1401 督办应答按行内结果码发送并推进应答状态")
    void should_send_urge_todo_ack() {
        CmJt809Command row = row(7L, Jt809Constants.MSG_WARN_URGE_TODO);
        row.setSubCmd(Jt809Constants.SUB_TYPE_URGE_TODO_ACK);
        row.setPlateNo("甘A43060");
        row.setPlateColor(1);
        row.setCmdData("1;1");
        when(commandService.listPending(20)).thenReturn(List.of(row));
        when(commandService.claim(7L)).thenReturn(true);
        when(govCommandService.sendUrgeTodoAckBody("甘A43060", 1, 1L, 1)).thenReturn(true);

        executor.runOnce();

        verify(govCommandService).sendUrgeTodoAckBody("甘A43060", 1, 1L, 1);
        verify(commandService).markResult(7L, GovCommandStatus.SENT, "[0x1401]报警督办应答：已写入链路");
        verifyNoInteractions(upstreamClient);
    }

    @Test
    @DisplayName("督办应答结果码非法时置失败且不调用链路")
    void should_fail_when_urge_todo_ack_payload_broken() {
        CmJt809Command row = row(8L, Jt809Constants.MSG_WARN_URGE_TODO);
        row.setSubCmd(Jt809Constants.SUB_TYPE_URGE_TODO_ACK);
        row.setPlateNo("甘A43060");
        row.setCmdData("bad-payload");
        when(commandService.listPending(20)).thenReturn(List.of(row));
        when(commandService.claim(8L)).thenReturn(true);

        executor.runOnce();

        verify(commandService).markResult(8L, GovCommandStatus.FAILED,
                "[0x1401]报警督办应答：链路不可用（重连后补发）");
        verifyNoInteractions(govCommandService, upstreamClient);
    }

    @Test
    @DisplayName("enabled=false 时不处理任何命令")
    void should_skip_when_disabled() {
        properties.setEnabled(false);

        executor.runOnce();

        verifyNoInteractions(commandService, upstreamClient, linkManager);
    }

    private CmJt809Command row(long id, int linkCmd) {
        CmJt809Command row = new CmJt809Command();
        row.setId(id);
        row.setCmd(linkCmd);
        row.setSource("SEND");
        row.setCommandStatus(GovCommandStatus.PENDING);
        return row;
    }
} 
