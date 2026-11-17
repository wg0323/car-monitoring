package com.cm.infrastructure.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 RabbitMQ 上的发布可靠性：可路由消息拿到 confirm ack，
 * mandatory 下不可路由消息产生 return，broker 不可达时发布直接抛异常交给 Outbox 重试。
 * 默认不跑，设置 CM_P3_MQ_TEST=true 启用；只用独立临时交换机与队列，用后删除。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_MQ_TEST", matches = "true")
class CommandPublishReliabilityTest {

    private static final String HOST = System.getenv().getOrDefault("CM_MQ_HOST", "127.0.0.1");

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("CM_MQ_PORT", "5672"));

    private static final String USERNAME = System.getenv().getOrDefault("CM_MQ_USERNAME", "cm_app");

    private static final String PASSWORD = System.getenv().getOrDefault("CM_MQ_PASSWORD", "CmApp@2026");

    private static final String VHOST = System.getenv().getOrDefault("CM_MQ_VHOST", "cm");

    private static final long WAIT_MILLIS = 5000L;

    private static final long POLL_MILLIS = 20L;

    private final List<ReturnedMessage> returned = new CopyOnWriteArrayList<>();

    private final BlockingQueue<Boolean> confirms = new LinkedBlockingQueue<>();

    private CachingConnectionFactory connectionFactory;

    private RabbitAdmin rabbitAdmin;

    private RabbitTemplate rabbitTemplate;

    private String exchangeName;

    private String queueName;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(HOST, PORT);
        connectionFactory.setUsername(USERNAME);
        connectionFactory.setPassword(PASSWORD);
        connectionFactory.setVirtualHost(VHOST);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> confirms.offer(ack));
        rabbitTemplate.setReturnsCallback(returned::add);

        exchangeName = "p3test.exchange." + UUID.randomUUID();
        queueName = "p3test.queue." + UUID.randomUUID();
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(exchangeName, false, true);
        Queue queue = new Queue(queueName, false, false, true);
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("bound"));
    }

    @AfterEach
    void tearDown() {
        if (rabbitAdmin != null) {
            rabbitAdmin.deleteQueue(queueName);
            rabbitAdmin.deleteExchange(exchangeName);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("可路由消息拿到 confirm ack 且不产生 return")
    void should_confirm_when_routed() throws Exception {
        rabbitTemplate.convertAndSend(exchangeName, "bound", "{\"eventType\":\"COMMAND_DOWNLINK\"}");

        Boolean ack = confirms.poll(WAIT_MILLIS, TimeUnit.MILLISECONDS);

        assertEquals(Boolean.TRUE, ack);
        assertTrue(returned.isEmpty());
    }

    @Test
    @DisplayName("mandatory 下不可路由消息产生 return，不能当成已投递")
    void should_return_when_not_routable() throws Exception {
        rabbitTemplate.convertAndSend(exchangeName, "unbound", "{\"eventType\":\"COMMAND_DOWNLINK\"}");

        waitUntilReturned();

        assertEquals(1, returned.size());
        assertEquals(exchangeName, returned.get(0).getExchange());
        assertEquals("unbound", returned.get(0).getRoutingKey());
    }

    @Test
    @DisplayName("broker 不可达时发布直接抛异常，由 Outbox 退避重试")
    void should_throw_when_broker_unreachable() {
        CachingConnectionFactory deadFactory = new CachingConnectionFactory(HOST, 1);
        deadFactory.setConnectionTimeout(1000);
        try {
            RabbitTemplate deadTemplate = new RabbitTemplate(deadFactory);
            deadTemplate.setMandatory(true);

            assertThrows(AmqpException.class,
                    () -> deadTemplate.convertAndSend(exchangeName, "bound", "{}"));
        } finally {
            deadFactory.destroy();
        }
    }

    private void waitUntilReturned() throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (returned.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS);
        }
    }
}
