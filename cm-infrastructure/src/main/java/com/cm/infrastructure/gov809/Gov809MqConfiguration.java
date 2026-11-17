package com.cm.infrastructure.gov809;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 809 转发拓扑。forward 队列直接绑到既有的 cm.telemetry 交换机，
 * 网关不需要多发一条消息：同一 SIM 的定位在四条队列内部各自保序，809 链路只看 forward 这一条。
 * 拓扑只由 cm-integration-809 声明（declare-enabled），避免管理端进程抢先改名或改参数。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({Gov809MqProperties.class, Gov809OutboxProperties.class})
public class Gov809MqConfiguration {

    private static final String QUORUM = "quorum";

    private final Gov809MqProperties properties;

    @Bean
    @ConditionalOnProperty(prefix = "cm.gov809.mq", name = "declare-enabled", havingValue = "true")
    public Declarables gov809Topology() {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        TopicExchange deadLetterExchange = new TopicExchange(properties.getDeadLetterExchange(), true, false);
        Queue deadQueue = QueueBuilder.durable(properties.getDeadQueue()).build();
        Queue forwardQueue = businessQueue(properties.getForwardQueue());
        Binding forwardBinding = BindingBuilder.bind(forwardQueue).to(exchange)
                .with(properties.getRoutingKeyPattern());
        Binding deadBinding = BindingBuilder.bind(deadQueue).to(deadLetterExchange)
                .with(properties.getDeadQueue());
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(exchange);
        declarables.add(deadLetterExchange);
        declarables.add(deadQueue);
        declarables.add(forwardQueue);
        declarables.add(forwardBinding);
        declarables.add(deadBinding);
        return new Declarables(declarables);
    }

    private Queue businessQueue(String queueName) {
        QueueBuilder builder = QueueBuilder.durable(queueName)
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getDeadQueue());
        if (QUORUM.equalsIgnoreCase(properties.getQueueType())) {
            builder.quorum();
        }
        return builder.build();
    }
}
 
