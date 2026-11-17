package com.cm.infrastructure.command;

import com.cm.infrastructure.service.CmCommandOutboxStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 发布确认回调。
 * ack 且未 return 才算已发 MQ；nack 或 return 都回到退避重发，不允许用 ack 覆盖 return 结果。
 * 回调只做条件状态更新，不在这里重发，重发统一由 relay 与超时回收任务调度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandPublishCallback implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private static final String PUBLISH_ATTEMPT_HEADER = "publishAttempt";

    private static final String REASON_RETURNED = "RETURNED";

    private final CmCommandOutboxStateService outboxStateService;

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!(correlationData instanceof CommandPublishCorrelationData data)) {
            return;
        }
        if (ack && data.getReturned() == null) {
            outboxStateService.confirm(data.getCommandId(), data.getPublishAttempt());
            return;
        }
        String reason = ack ? REASON_RETURNED : "NACK " + cause;
        outboxStateService.fail(data.getCommandId(), data.getPublishAttempt(), reason);
    }

    /**
     * 不可路由的消息先生成 return，随后仍可能收到 ack；这里先把状态退回可重试，
     * 迟到的 ack 因发布状态已改变而无法把它改回成功。
     */
    @Override
    public void returnedMessage(ReturnedMessage returned) {
        MessageProperties properties = returned.getMessage().getMessageProperties();
        String commandId = properties.getMessageId();
        Object attempt = properties.getHeaders().get(PUBLISH_ATTEMPT_HEADER);
        log.warn("指令消息未路由到队列, commandId={}, exchange={}, routingKey={}, replyCode={}",
                commandId, returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode());
        if (commandId == null || !(attempt instanceof Number number)) {
            return;
        }
        outboxStateService.fail(commandId, number.intValue(), REASON_RETURNED);
    }
}
