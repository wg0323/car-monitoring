package com.cm.infrastructure.command;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指令链路 MQ 拓扑。
 * 下行队列按网关实例划分，网关之间不竞争；应答队列多实例竞争消费，幂等由数据库条件更新保证。
 * 两个业务队列都挂 DLX：本地重试耗尽后进死信，由死信消费者落库，不做无限 requeue。
 */
@Configuration
@RequiredArgsConstructor
public class CmCommandMqConfiguration {

    private static final String QUORUM = "quorum";

    private final CmCommandMqProperties properties;

    @Bean
    public TopicExchange commandExchange() {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public TopicExchange commandDeadLetterExchange() {
        return new TopicExchange(properties.getDeadLetterExchange(), true, false);
    }

    /**
     * 下行队列由网关实例声明，管理端不声明，避免多出来一条无人消费的队列。
     */
    @Bean
    @ConditionalOnProperty(prefix = "cm.command.mq", name = "downlink-enabled", havingValue = "true")
    public Queue commandDownlinkQueue() {
        return businessQueue(properties.getDownlinkQueue(), properties.getDownlinkDeadQueue());
    }

    @Bean
    public Queue commandAckQueue() {
        return businessQueue(properties.getAckQueue(), properties.getAckDeadQueue());
    }

    @Bean
    public Queue commandDownlinkDeadQueue() {
        return QueueBuilder.durable(properties.getDownlinkDeadQueue()).build();
    }

    @Bean
    public Queue commandAckDeadQueue() {
        return QueueBuilder.durable(properties.getAckDeadQueue()).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cm.command.mq", name = "downlink-enabled", havingValue = "true")
    public Binding commandDownlinkBinding() {
        return BindingBuilder.bind(commandDownlinkQueue()).to(commandExchange())
                .with(properties.getDownlinkRoutingKey());
    }

    @Bean
    public Binding commandAckBinding() {
        return BindingBuilder.bind(commandAckQueue()).to(commandExchange())
                .with(properties.getAckQueue());
    }

    @Bean
    public Binding commandDownlinkDeadBinding() {
        return BindingBuilder.bind(commandDownlinkDeadQueue()).to(commandDeadLetterExchange())
                .with(properties.getDownlinkDeadQueue());
    }

    @Bean
    public Binding commandAckDeadBinding() {
        return BindingBuilder.bind(commandAckDeadQueue()).to(commandDeadLetterExchange())
                .with(properties.getAckDeadQueue());
    }

    /**
     * 死信专用监听容器：落库失败时重新入队，宁可积压也不丢证据。
     * 业务队列仍使用默认容器（重试耗尽后拒收进 DLX），两者语义相反，不能共用。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory deadLetterContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(true);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }

    /**
     * 指令发布专用模板：开启 mandatory 与 confirm/return 回调，与业务其他模板隔离。
     */
    @Bean
    public RabbitTemplate commandRabbitTemplate(ConnectionFactory connectionFactory,
                                                CommandPublishCallback publishCallback) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback(publishCallback);
        template.setReturnsCallback(publishCallback);
        template.setMessageConverter(new SimpleMessageConverter());
        return template;
    }

    private Queue businessQueue(String queueName, String deadQueueName) {
        QueueBuilder builder = QueueBuilder.durable(queueName)
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(deadQueueName);
        if (QUORUM.equalsIgnoreCase(properties.getQueueType())) {
            builder.quorum();
        }
        return builder.build();
    }
}
