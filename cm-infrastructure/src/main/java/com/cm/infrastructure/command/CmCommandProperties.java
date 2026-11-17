package com.cm.infrastructure.command;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 指令链路配置。relay 只在管理端进程开启，网关只做下行消费与上行转发。
 */
@Data
@ConfigurationProperties(prefix = "cm.command")
public class CmCommandProperties {

    /** 网关实例标识，决定下行队列 command.downlink.{gatewayId}。 */
    private String gatewayId = "gw-1";

    /** Redis 路由 TTL，网关崩溃后由租约兜底。 */
    private int routeTtlSeconds = 300;

    /** 每次尝试的超时窗口，从写终端成功起算。 */
    private int ackTimeoutSeconds = 60;

    private final Relay relay = new Relay();

    /**
     * Outbox relay 与重试参数。
     */
    @Data
    public static class Relay {

        /** 只在管理端进程开启，网关保持 false。 */
        private boolean enabled = false;

        /** 租约时长，到期后可被其他实例接手。 */
        private int leaseSeconds = 30;

        /** confirm 等待窗口：发布后超过该时长仍无回调，由超时任务回收重发。 */
        private int confirmTimeoutSeconds = 30;

        /** 单次抢占用量。 */
        private int batchSize = 50;

        /** 发布尝试上限，耗尽后置 FAILED 并落死信。 */
        private int maxAttempts = 5;

        /** 退避首个间隔。 */
        private long backoffBaseMillis = 1000L;

        /** 退避上限。 */
        private long backoffMaxMillis = 60000L;

        /** relay 轮询间隔。 */
        private long intervalMillis = 500L;

        /** 租约超时回收与指令超时清扫间隔。 */
        private long sweepIntervalMillis = 30000L;
    }
}
