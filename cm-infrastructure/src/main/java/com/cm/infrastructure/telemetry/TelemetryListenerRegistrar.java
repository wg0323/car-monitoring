package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 定位链路消费者注册。每个分片队列单独一个容器、单消费者，
 * 保证同一 SIM 的事件在片内按投递顺序处理；容器级重试三次后拒绝并进死信，
 * 不做无限 requeue。注解无法表达运行期分片数，因此用 SmartLifecycle 动态创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.telemetry.mq", name = "consumers-enabled", havingValue = "true")
public class TelemetryListenerRegistrar implements SmartLifecycle {

    /** 首次投递 + 两次重试，共三次尝试。 */
    private static final int MAX_RETRIES = 2;

    private static final long INITIAL_INTERVAL_MILLIS = 1000L;

    private static final double MULTIPLIER = 2.0;

    private static final long MAX_INTERVAL_MILLIS = 10000L;

    private final ConnectionFactory connectionFactory;

    private final TelemetryMqProperties mqProperties;

    private final TelemetryHistoryHandler historyHandler;

    private final TelemetryLatestHandler latestHandler;

    private final TelemetryAlarmHandler alarmHandler;

    private final List<SimpleMessageListenerContainer> containers = new ArrayList<>();

    private volatile boolean running;

    @Override
    public void start() {
        for (int shard = 0; shard < mqProperties.getShardCount(); shard++) {
            containers.add(createContainer(mqProperties.historyQueue(shard), historyHandler::handle));
            containers.add(createContainer(mqProperties.latestQueue(shard), latestHandler::handle));
            containers.add(createContainer(mqProperties.alarmQueue(shard), alarmHandler::handle));
        }
        containers.forEach(SimpleMessageListenerContainer::start);
        running = true;
        log.info("定位链路消费者已启动, shardCount={}, queueCount={}",
                mqProperties.getShardCount(), containers.size());
    }

    @Override
    public void stop() {
        containers.forEach(SimpleMessageListenerContainer::stop);
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private SimpleMessageListenerContainer createContainer(String queueName, MessageHandler handler) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);
        container.setPrefetchCount(mqProperties.getPrefetchCount());
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setDefaultRequeueRejected(false);
        container.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(MAX_RETRIES)
                .backOffOptions(INITIAL_INTERVAL_MILLIS, MULTIPLIER, MAX_INTERVAL_MILLIS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        container.setMessageListener(message -> dispatch(queueName, message, handler));
        return container;
    }

    private void dispatch(String queueName, org.springframework.amqp.core.Message message, MessageHandler handler) {
        try {
            handler.handle(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            log.error("定位消息处理失败, queue={}, messageId={}, bodyLength={}", queueName,
                    message.getMessageProperties().getMessageId(), message.getBody().length, ex);
            throw ex;
        }
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(String payload);
    }
}
