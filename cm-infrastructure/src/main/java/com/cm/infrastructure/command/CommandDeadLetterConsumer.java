package com.cm.infrastructure.command;

import com.cm.infrastructure.service.CmMqDeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 死信消费者。只做持久化与告警，不在消费线程里重试原业务。
 * 落库本身失败时异常上抛，让消息留在 DLQ 而不是被 ACK 后丢弃。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.command.mq", name = "consumers-enabled", havingValue = "true",
        matchIfMissing = true)
public class CommandDeadLetterConsumer {

    private static final String REASON_DEAD_LETTER = "DEAD_LETTER";

    private final CmMqDeadLetterService deadLetterService;

    @RabbitListener(containerFactory = "deadLetterContainerFactory", queues = {
            "${cm.command.mq.downlink-dead-queue:command.downlink.dlq}",
            "${cm.command.mq.ack-dead-queue:command.ack.dlq}"})
    public void onMessage(Message message, @Header("amqp_consumerQueue") String queue) {
        log.error("收到指令链路死信, sourceQueue={}, messageId={}, bodyLength={}",
                queue, message.getMessageProperties().getMessageId(), message.getBody().length);
        deadLetterService.parkDeadMessage(message, queue, REASON_DEAD_LETTER);
    }
}
