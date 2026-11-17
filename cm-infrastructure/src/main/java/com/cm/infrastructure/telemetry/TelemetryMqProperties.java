package com.cm.infrastructure.telemetry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

/**
 * 定位链路 MQ 拓扑参数。history / latest / alarm 三个队列同分片数、同 routingKey 规则，
 * 保证同一 SIM 的位置与报警事件进入同一分片，报警判断与位置落库看到同一顺序。
 */
@Data
@ConfigurationProperties(prefix = "cm.telemetry.mq")
public class TelemetryMqProperties {

    private String exchange = "cm.telemetry";

    private String deadLetterExchange = "cm.telemetry.dlx";

    private String historyQueuePrefix = "telemetry.history";

    private String latestQueuePrefix = "telemetry.latest";

    private String alarmQueuePrefix = "telemetry.alarm";

    private String routingKeyPrefix = "telemetry.position";

    private String deadQueue = "telemetry.dead";

    /** 分片数，按 SIM 哈希取模；每片一个消费者以保证片内顺序。 */
    private int shardCount = 8;

    private String queueType = "classic";

    private int prefetchCount = 50;

    /** 拓扑声明与消费者只在接入网关开启，管理端保持 false。 */
    private boolean consumersEnabled = false;

    public String historyQueue(int shard) {
        return historyQueuePrefix + "." + shard;
    }

    public String latestQueue(int shard) {
        return latestQueuePrefix + "." + shard;
    }

    public String alarmQueue(int shard) {
        return alarmQueuePrefix + "." + shard;
    }

    public String routingKey(int shard) {
        return routingKeyPrefix + "." + shard;
    }

    public int shardOf(String simNo) {
        return Math.floorMod(Objects.hashCode(simNo), shardCount);
    }
}
