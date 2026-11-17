package com.cm.infrastructure.gov809;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import java.util.Collection;

/**
 * 809 转发拓扑：forward 队列必须挂死信并绑到既有 cm.telemetry 交换机，
 * 绑错路由键会让断链积压在运行时才暴露（消息直接进不了队列）。
 */
class Gov809MqTopologyTest {

    private static final String DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";

    private static final String DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    @Test
    @DisplayName("forward 队列绑定 cm.telemetry 的定位路由，并挂到 809 死信")
    void should_bind_forward_queue_to_telemetry_exchange() {
        Gov809MqProperties properties = new Gov809MqProperties();
        Declarables declarables = new Gov809MqConfiguration(properties).gov809Topology();

        Collection<Queue> queues = declarables.getDeclarablesByType(Queue.class);
        Collection<Binding> bindings = declarables.getDeclarablesByType(Binding.class);
        Queue forward = queues.stream()
                .filter(queue -> properties.getForwardQueue().equals(queue.getName()))
                .findFirst().orElse(null);

        assertNotNull(forward, "缺少队列 " + properties.getForwardQueue());
        assertEquals(properties.getDeadLetterExchange(),
                forward.getArguments().get(DEAD_LETTER_EXCHANGE));
        assertEquals(properties.getDeadQueue(),
                forward.getArguments().get(DEAD_LETTER_ROUTING_KEY));
        assertTrue(bindings.stream().anyMatch(binding -> properties.getForwardQueue()
                        .equals(binding.getDestination())
                        && properties.getExchange().equals(binding.getExchange())
                        && properties.getRoutingKeyPattern().equals(binding.getRoutingKey())),
                "forward 队列未绑定到 " + properties.getExchange());
        // 2 个交换机 + 死信队列 + 业务队列 + 2 条绑定。
        assertEquals(6, declarables.getDeclarables().size());
    }

    @Test
    @DisplayName("默认只由 integration 声明拓扑，其他进程读配置不声明")
    void should_not_declare_topology_by_default() {
        Gov809MqProperties properties = new Gov809MqProperties();

        assertEquals(false, properties.isDeclareEnabled());
        assertEquals("gov809.forward", properties.getForwardQueue());
    }
}
 
