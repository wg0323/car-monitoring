package com.cm.infrastructure.telemetry;

import com.cm.infrastructure.P3TestApplication;
import com.cm.infrastructure.service.CmMqDeadLetterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 真实 broker 上的定位链路死信闭环：消费抛异常 → DLX → 死信队列 → 落 cm_mq_dead_letter。
 * 用独立临时交换机、队列与死信队列，不碰常驻的 telemetry.* 生产队列。
 * 默认不跑，同时设置 CM_P3_MQ_TEST 与 CM_P3_DB_TEST 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_MQ_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = P3TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
class TelemetryDeadLetterFlowTest {

    private static final String HOST = System.getenv().getOrDefault("CM_MQ_HOST", "127.0.0.1");

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("CM_MQ_PORT", "5672"));

    private static final String USERNAME = System.getenv().getOrDefault("CM_MQ_USERNAME", "cm_app");

    private static final String PASSWORD = System.getenv().getOrDefault("CM_MQ_PASSWORD", "CmApp@2026");

    private static final String VHOST = System.getenv().getOrDefault("CM_MQ_VHOST", "cm");

    private static final long WAIT_MILLIS = 10000L;

    private static final long POLL_MILLIS = 50L;

    @Autowired
    private CmMqDeadLetterService deadLetterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CachingConnectionFactory connectionFactory;

    private RabbitAdmin rabbitAdmin;

    private RabbitTemplate rabbitTemplate;

    private SimpleMessageListenerContainer consumerContainer;

    private String exchangeName;

    private String queueName;

    private String deadQueueName;

    private String deadExchangeName;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(HOST, PORT);
        connectionFactory.setUsername(USERNAME);
        connectionFactory.setPassword(PASSWORD);
        connectionFactory.setVirtualHost(VHOST);
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitTemplate = new RabbitTemplate(connectionFactory);

        String suffix = UUID.randomUUID().toString();
        exchangeName = "p3test.dlx.exchange." + suffix;
        deadExchangeName = "p3test.dlx.dead." + suffix;
        queueName = "p3test.dlx.queue." + suffix;
        deadQueueName = "p3test.dlx.dead." + suffix;

        TopicExchange exchange = new TopicExchange(exchangeName, false, true);
        TopicExchange deadExchange = new TopicExchange(deadExchangeName, false, true);
        Queue deadQueue = new Queue(deadQueueName, false, false, true);
        Queue business = QueueBuilder.nonDurable(queueName)
                .deadLetterExchange(deadExchangeName)
                .deadLetterRoutingKey(deadQueueName)
                .build();
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareExchange(deadExchange);
        rabbitAdmin.declareQueue(deadQueue);
        rabbitAdmin.declareQueue(business);
        rabbitAdmin.declareBinding(BindingBuilder.bind(deadQueue).to(deadExchange).with(deadQueueName));
        rabbitAdmin.declareBinding(BindingBuilder.bind(business).to(exchange).with("poison"));

        consumerContainer = new SimpleMessageListenerContainer(connectionFactory);
        consumerContainer.setQueueNames(queueName);
        consumerContainer.setDefaultRequeueRejected(false);
        consumerContainer.setMessageListener(message -> {
            throw new IllegalStateException("故意让消费失败");
        });
        consumerContainer.start();
    }

    @AfterEach
    void tearDown() {
        if (consumerContainer != null) {
            consumerContainer.stop();
            consumerContainer.destroy();
        }
        if (rabbitAdmin != null) {
            rabbitAdmin.deleteQueue(deadQueueName);
            rabbitAdmin.deleteQueue(queueName);
            rabbitAdmin.deleteExchange(deadExchangeName);
            rabbitAdmin.deleteExchange(exchangeName);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("消费抛异常后消息进死信队列，落库时保留来源队列与原始路由")
    void should_park_dead_letter_after_consumer_failure() throws Exception {
        // 死信落库按消息体摘要去重，重用同一段 payload 会命中上一次的记录，所以每次带一个唯一后缀。
        byte[] payload = ("{not-a-json-event-" + UUID.randomUUID() + "}").getBytes(StandardCharsets.UTF_8);
        rabbitTemplate.convertAndSend(exchangeName, "poison", payload);

        Message dead = receiveDead();
        assertNotNull(dead, "消息应在超时前进入死信队列");

        TelemetryDeadLetterConsumer consumer = new TelemetryDeadLetterConsumer(deadLetterService);
        consumer.onMessage(dead, deadQueueName);

        Integer parked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cm_mq_dead_letter"
                        + " WHERE source_queue = ? AND reason = ? AND dead_status = 'PENDING'",
                Integer.class, deadQueueName, "TELEMETRY_DEAD_LETTER");
        assertEquals(1, parked);
    }

    private Message receiveDead() throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(deadQueueName);
            if (message != null) {
                return message;
            }
            Thread.sleep(POLL_MILLIS);
        }
        return null;
    }
}
