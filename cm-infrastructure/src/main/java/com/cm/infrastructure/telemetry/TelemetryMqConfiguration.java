package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 定位链路 MQ 拓扑。一个 routingKey 同时绑定 history、latest 与 alarm 三个队列，
 * 一次发布被三条消费链路各自处理：三条链路都按 SIM 分片、每片单消费者，
 * 因此同一台车的位置与报警在各自链路内部保持投递顺序。
 * 定位消息不进入 Outbox：终端超时重报会带来同一 eventId，消费端按它去重。
 */
@Configuration
@RequiredArgsConstructor
public class TelemetryMqConfiguration {

    private static final String QUORUM = "quorum";

    private final TelemetryMqProperties properties;

    @Bean
    @ConditionalOnProperty(prefix = "cm.telemetry.mq", name = "consumers-enabled", havingValue = "true")
    public Declarables telemetryTopology() {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        TopicExchange deadLetterExchange = new TopicExchange(properties.getDeadLetterExchange(), true, false);
        Queue deadQueue = QueueBuilder.durable(properties.getDeadQueue()).build();
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(exchange);
        declarables.add(deadLetterExchange);
        declarables.add(deadQueue);
        declarables.add(BindingBuilder.bind(deadQueue).to(deadLetterExchange).with(properties.getDeadQueue()));
        for (int shard = 0; shard < properties.getShardCount(); shard++) {
            Queue history = businessQueue(properties.historyQueue(shard));
            Queue latest = businessQueue(properties.latestQueue(shard));
            Queue alarm = businessQueue(properties.alarmQueue(shard));
            declarables.add(history);
            declarables.add(latest);
            declarables.add(alarm);
            Binding historyBinding = BindingBuilder.bind(history).to(exchange).with(properties.routingKey(shard));
            Binding latestBinding = BindingBuilder.bind(latest).to(exchange).with(properties.routingKey(shard));
            Binding alarmBinding = BindingBuilder.bind(alarm).to(exchange).with(properties.routingKey(shard));
            declarables.add(historyBinding);
            declarables.add(latestBinding);
            declarables.add(alarmBinding);
        }
        return new Declarables(declarables);
    }

    /**
     * 定位发布专用模板：mandatory 与 confirm/return 回调独立于指令链路，
     * 两条链路的回调互不干扰。
     */
    @Bean
    public RabbitTemplate telemetryRabbitTemplate(ConnectionFactory connectionFactory,
                                                  TelemetryPublishCallback publishCallback) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback(publishCallback);
        template.setReturnsCallback(publishCallback);
        template.setMessageConverter(new SimpleMessageConverter());
        return template;
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
