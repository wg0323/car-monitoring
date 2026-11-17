package com.cm.infrastructure.command;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 指令链路 MQ 拓扑参数。队列名默认带网关标识，测试通过覆盖前缀避免污染真实队列。
 */
@Data
@ConfigurationProperties(prefix = "cm.command.mq")
public class CmCommandMqProperties {

    private String exchange = "cm.command";

    private String deadLetterExchange = "cm.command.dlx";

    /** 本网关独占的下行队列，网关实例之间不竞争。 */
    private String downlinkQueue = "command.downlink.gw-1";

    /** 下行队列的 routingKey，与队列名一致便于排查。 */
    private String downlinkRoutingKey = "command.downlink.gw-1";

    /** 终端应答队列，多个网关实例竞争消费，处理逻辑只依赖数据库条件更新。 */
    private String ackQueue = "command.ack";

    private String downlinkDeadQueue = "command.downlink.dlq";

    private String ackDeadQueue = "command.ack.dlq";

    /** classic 适用于本机 3.8；生产 profile 覆盖为 quorum。 */
    private String queueType = "classic";
}
