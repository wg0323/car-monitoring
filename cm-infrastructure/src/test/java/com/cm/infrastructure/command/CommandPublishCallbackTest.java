package com.cm.infrastructure.command;

import com.cm.infrastructure.service.CmCommandOutboxStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 发布确认回调：只有 ack 且未 return 才算已发 MQ，其余一律回到可重试状态。
 */
@ExtendWith(MockitoExtension.class)
class CommandPublishCallbackTest {

    private static final String COMMAND_ID = "cmd-0001";

    @Mock
    private CmCommandOutboxStateService outboxStateService;

    private CommandPublishCallback callback;

    @BeforeEach
    void setUp() {
        callback = new CommandPublishCallback(outboxStateService);
    }

    @Test
    @DisplayName("confirm=ack 且没有 return 时标记已确认")
    void should_confirm_when_ack_without_return() {
        callback.confirm(new CommandPublishCorrelationData(COMMAND_ID, 2), true, null);

        verify(outboxStateService).confirm(COMMAND_ID, 2);
        verify(outboxStateService, never()).fail(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("return 已经发生时，迟到的 ack 不能把记录改成成功")
    void should_fail_when_ack_arrives_after_return() {
        CommandPublishCorrelationData data = new CommandPublishCorrelationData(COMMAND_ID, 2);
        data.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "cm.command", "rk"));

        callback.confirm(data, true, null);

        verify(outboxStateService).fail(COMMAND_ID, 2, "RETURNED");
        verify(outboxStateService, never()).confirm(anyString(), anyInt());
    }

    @Test
    @DisplayName("nack 带原因进入重试")
    void should_fail_when_nack() {
        callback.confirm(new CommandPublishCorrelationData(COMMAND_ID, 1), false, "broker down");

        verify(outboxStateService).fail(COMMAND_ID, 1, "NACK broker down");
    }

    @Test
    @DisplayName("return 回调按消息头定位发布尝试并退回重试")
    void should_fail_when_message_returned() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(COMMAND_ID);
        properties.setHeader("publishAttempt", 4);
        ReturnedMessage returned = new ReturnedMessage(new Message(new byte[0], properties),
                312, "NO_ROUTE", "cm.command", "command.downlink.gw-1");

        callback.returnedMessage(returned);

        verify(outboxStateService).fail(COMMAND_ID, 4, "RETURNED");
    }

    @Test
    @DisplayName("重放等无业务归属的发布不触碰 Outbox 状态")
    void should_ignore_unknown_correlation() {
        callback.confirm(new CorrelationData("replay-1"), true, null);

        verifyNoInteractions(outboxStateService);
    }
}
