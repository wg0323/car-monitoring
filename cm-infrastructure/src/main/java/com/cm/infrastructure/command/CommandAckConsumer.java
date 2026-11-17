package com.cm.infrastructure.command;

import com.cm.infrastructure.service.CmCommandAckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * command.ack 消费者。多个网关实例竞争消费，处理逻辑只依赖数据库条件更新，天然可重入。
 * 解析失败属于不可恢复异常，直接拒绝并入死信，不无限重投。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.command.mq", name = "consumers-enabled", havingValue = "true",
        matchIfMissing = true)
public class CommandAckConsumer {

    private final CmCommandAckService ackService;

    @RabbitListener(queues = "${cm.command.mq.ack-queue:command.ack}")
    public void onMessage(Message message) {
        CommandAckEvent event;
        try {
            event = CommandJson.read(new String(message.getBody(), StandardCharsets.UTF_8), CommandAckEvent.class);
        } catch (RuntimeException ex) {
            log.error("终端应答事件无法解析，转入死信, messageId={}",
                    message.getMessageProperties().getMessageId(), ex);
            throw new AmqpRejectAndDontRequeueException("终端应答事件无法解析", ex);
        }
        ackService.handle(event);
    }
}
