package com.cm.infrastructure.telemetry;

import com.cm.infrastructure.service.CmMqDeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 定位链路死信消费者。只做持久化，不在消费线程里重试原业务；
 * 落库失败时异常上抛，让消息留在死信队列而不是被确认后丢弃。
 * 重放复用 CmMqDeadLetterService：原始路由取自 x-death，事件 ID 沿用原消息 ID。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.telemetry.mq", name = "consumers-enabled", havingValue = "true")
public class TelemetryDeadLetterConsumer {

    private static final String REASON_DEAD_LETTER = "TELEMETRY_DEAD_LETTER";

    private final CmMqDeadLetterService deadLetterService;

    @RabbitListener(containerFactory = "deadLetterContainerFactory",
            queues = "${cm.telemetry.mq.dead-queue:telemetry.dead}")
    public void onMessage(Message message, @Header("amqp_consumerQueue") String queue) {
        log.error("收到定位链路死信, sourceQueue={}, messageId={}, bodyLength={}",
                queue, message.getMessageProperties().getMessageId(), message.getBody().length);
        deadLetterService.parkDeadMessage(message, queue, REASON_DEAD_LETTER);
    }
}
