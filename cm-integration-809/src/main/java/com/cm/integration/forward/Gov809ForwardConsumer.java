package com.cm.integration.forward;

import com.cm.infrastructure.gov809.Gov809MqProperties;
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

/**
 * gov809.forward 消费者。单队列单消费者：809 链路本身就是一条有序连接，
 * 再分片只会让同一链路出现多个写入者，补发的序号也就失去意义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.gov809.mq", name = "consume-enabled", havingValue = "true")
public class Gov809ForwardConsumer implements SmartLifecycle {

    private static final int MAX_RETRIES = 2;

    private static final long INITIAL_INTERVAL_MILLIS = 1000L;

    private static final double MULTIPLIER = 2.0D;

    private static final long MAX_INTERVAL_MILLIS = 10000L;

    private final ConnectionFactory connectionFactory;

    private final Gov809MqProperties mqProperties;

    private final Gov809ForwardHandler forwardHandler;

    private volatile SimpleMessageListenerContainer container;

    private volatile boolean running;

    @Override
    public void start() {
        SimpleMessageListenerContainer listener = new SimpleMessageListenerContainer(connectionFactory);
        listener.setQueueNames(mqProperties.getForwardQueue());
        listener.setConcurrentConsumers(1);
        listener.setMaxConcurrentConsumers(1);
        listener.setPrefetchCount(mqProperties.getPrefetchCount());
        listener.setAcknowledgeMode(AcknowledgeMode.AUTO);
        listener.setDefaultRequeueRejected(false);
        listener.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(MAX_RETRIES)
                .backOffOptions(INITIAL_INTERVAL_MILLIS, MULTIPLIER, MAX_INTERVAL_MILLIS)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        listener.setMessageListener(message -> forwardHandler.handle(
                new String(message.getBody(), StandardCharsets.UTF_8)));
        listener.start();
        container = listener;
        running = true;
        log.info("809 转发消费者已启动, queue={}", mqProperties.getForwardQueue());
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
            container = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
 
