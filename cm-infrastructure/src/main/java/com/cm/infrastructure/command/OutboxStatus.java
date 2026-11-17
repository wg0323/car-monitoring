package com.cm.infrastructure.command;

/**
 * cm_command_outbox.publish_status 的取值。
 * PUBLISHED 表示已发起发布、等待 confirm，等同其他系统中的 SENDING；
 * 该状态超过租约仍未收到回调时由超时任务回收，不能当成成功。
 */
public final class OutboxStatus {

    /** 待发布，relay 的抢占条件。 */
    public static final String PENDING = "PENDING";

    /** 已发起发布，等待 confirm 与 return 结果。 */
    public static final String PUBLISHED = "PUBLISHED";

    /** Broker confirm=ack 且未 return。 */
    public static final String CONFIRMED = "CONFIRMED";

    /** 重试耗尽或确定性失败，保留 payload 供重放。 */
    public static final String FAILED = "FAILED";

    /** 指令已进入终态，不再发布。 */
    public static final String SKIPPED = "SKIPPED";

    private OutboxStatus() {
    }
}
