package com.cm.gateway.downlink;

import com.cm.infrastructure.command.CmCommandMqProperties;
import com.cm.infrastructure.command.CommandAckEvent;
import com.cm.infrastructure.command.CommandJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 终端应答转发。网关只把 (simNo, 流水号, incarnation) 与应答结果交给 command.ack，
 * 由消费端回查归属，网关不做任何业务归属判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandAckPublisher {

    private final RabbitTemplate commandRabbitTemplate;

    private final CmCommandMqProperties mqProperties;

    public void publish(CommandAckEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId());
        byte[] body = CommandJson.write(event).getBytes(StandardCharsets.UTF_8);
        commandRabbitTemplate.send(mqProperties.getExchange(), mqProperties.getAckQueue(),
                new Message(body, properties));
    }
}
