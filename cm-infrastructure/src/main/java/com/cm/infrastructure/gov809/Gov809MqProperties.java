package com.cm.infrastructure.gov809;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 809 转发队列参数。gov809.forward 直接绑定既有 cm.telemetry 交换机，
 * 网关零改动：定位事件一次发布，由 history/latest/alarm/forward 四条队列各自独立消费。
 */
@Data
@ConfigurationProperties(prefix = "cm.gov809.mq")
public class Gov809MqProperties {

    private String exchange = "cm.telemetry";

    private String routingKeyPattern = "telemetry.position.*";

    private String forwardQueue = "gov809.forward";

    private String deadLetterExchange = "cm.gov809.dlx";

    private String deadQueue = "gov809.forward.dlq";

    private String queueType = "classic";

    private int prefetchCount = 50;

    /** 拓扑只由 cm-integration-809 声明；其他进程读同一份配置但不声明。 */
    private boolean declareEnabled = false;
}

