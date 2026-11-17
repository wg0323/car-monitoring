package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CommandPublishCorrelationData;
import com.cm.infrastructure.service.CmCommandPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 指令消息发布实现。
 * 消息体是已序列化好的版本化 JSON，直接按字节发送，不让转换器再做一次编码判断。
 */
@Service
@RequiredArgsConstructor
public class CmCommandPublisherImpl implements CmCommandPublisher {

    private static final String PUBLISH_ATTEMPT_HEADER = "publishAttempt";

    private final RabbitTemplate commandRabbitTemplate;

    @Override
    public void publishDownlink(String commandId, int publishAttempt, String exchange,
                                String routingKey, String payload) {
        Message message = buildMessage(payload);
        message.getMessageProperties().setMessageId(commandId);
        message.getMessageProperties().setHeader(PUBLISH_ATTEMPT_HEADER, publishAttempt);
        commandRabbitTemplate.send(exchange, routingKey, message,
                new CommandPublishCorrelationData(commandId, publishAttempt));
    }

    @Override
    public void replay(String exchange, String routingKey, String messageId, String payload) {
        Message message = buildMessage(payload);
        message.getMessageProperties().setMessageId(messageId);
        commandRabbitTemplate.send(exchange, routingKey, message);
    }

    private Message buildMessage(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
