package com.cm.infrastructure.gov809;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 809 积压队列容量参数。旧系统用无界内存队列（上限 200 万且会主动清空），
 * 这里改成有界持久队列：超限只丢最老的定位，督办等非定位报文保留。
 */
@Data
@ConfigurationProperties(prefix = "cm.gov809.outbox")
public class Gov809OutboxProperties {

    /** 单链路待补发上限，超过后按批裁剪最老的定位报文。 */
    private long maxPending = 200_000L;

    private int pruneBatch = 1_000;

    /** 单条报文写入链路失败的最大重试次数，超过后置 FAILED 留待人工处理。 */
    private int maxAttempt = 5;
}

