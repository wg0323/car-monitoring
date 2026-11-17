package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 定位事件发布。消息按 SIM 哈希进入固定分片，一批事件共享一个确认聚合器；
 * 发布抛异常时立即把该批标记失败，避免调用方空等整个确认超时窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryPublisher {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final RabbitTemplate telemetryRabbitTemplate;

    private final TelemetryMqProperties mqProperties;

    public TelemetryPublishTracker publish(PositionEvent event) {
        return publishBatch(List.of(event));
    }

    public TelemetryPublishTracker publishBatch(List<PositionEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events must not be empty");
        }
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(events.size());
        for (PositionEvent event : events) {
            try {
                send(event, tracker);
            } catch (RuntimeException ex) {
                log.error("定位事件发布失败, eventId={}", event.eventId(), ex);
                tracker.onFailure();
            }
        }
        return tracker;
    }

    private void send(PositionEvent event, TelemetryPublishTracker tracker) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId());
        properties.setHeader(EVENT_TYPE_HEADER, event.eventType());
        byte[] body = TelemetryJson.write(event).getBytes(StandardCharsets.UTF_8);
        telemetryRabbitTemplate.send(mqProperties.getExchange(),
                mqProperties.routingKey(mqProperties.shardOf(event.simNo())),
                new Message(body, properties), new TelemetryPublishCorrelationData(event.eventId(), tracker));
    }
}
