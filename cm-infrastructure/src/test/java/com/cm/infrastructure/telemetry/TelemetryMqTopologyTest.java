package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定位链路拓扑声明：history / latest / alarm 三个队列必须同分片数、同 routingKey、同死信配置，
 * 否则报警链路会自己在运行时才暴露问题（错过的消息直接进不了死信）。
 */
class TelemetryMqTopologyTest {

    private static final String DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";

    private static final String DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    @Test
    @DisplayName("每个分片的三个队列都与同一 routingKey 绑定，并挂同一个死信交换")
    void should_bind_three_queues_per_shard() {
        TelemetryMqProperties properties = new TelemetryMqProperties();
        Declarables declarables = new TelemetryMqConfiguration(properties).telemetryTopology();

        Collection<Queue> queues = declarables.getDeclarablesByType(Queue.class);
        Collection<Binding> bindings = declarables.getDeclarablesByType(Binding.class);

        int shards = properties.getShardCount();
        for (int shard = 0; shard < shards; shard++) {
            String routingKey = properties.routingKey(shard);
            for (String queueName : List.of(properties.historyQueue(shard),
                    properties.latestQueue(shard), properties.alarmQueue(shard))) {
                Queue queue = queueOf(queues, queueName);
                assertNotNull(queue, "缺少队列 " + queueName);
                assertEquals(properties.getDeadLetterExchange(),
                        queue.getArguments().get(DEAD_LETTER_EXCHANGE), queueName + " 未挂死信交换");
                assertEquals(properties.getDeadQueue(),
                        queue.getArguments().get(DEAD_LETTER_ROUTING_KEY), queueName + " 死信路由不对");
                assertTrue(bindings.stream().anyMatch(binding -> queueName.equals(binding.getDestination())
                        && routingKey.equals(binding.getRoutingKey())), queueName + " 未绑定到 " + routingKey);
            }
        }
        // 2 个交换机 + 1 个死信队列 + 1 条死信绑定 + 每分片 3 个队列与 3 条绑定。
        assertEquals(4 + shards * 6, declarables.getDeclarables().size());
    }

    @Test
    @DisplayName("分片路由同一 SIM 恒定同片，分片数取自配置")
    void should_route_same_sim_to_same_shard() {
        TelemetryMqProperties properties = new TelemetryMqProperties();

        int shard = properties.shardOf("13912345678");

        assertEquals(shard, properties.shardOf("13912345678"));
        assertTrue(shard >= 0 && shard < properties.getShardCount());
    }

    private static Queue queueOf(Collection<Queue> queues, String name) {
        return queues.stream().filter(queue -> name.equals(queue.getName())).findFirst().orElse(null);
    }
}
