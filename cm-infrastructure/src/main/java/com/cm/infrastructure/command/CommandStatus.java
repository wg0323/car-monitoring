package com.cm.infrastructure.command;

import java.util.Set;

/**
 * 指令状态机取值，对应方案 12.2 的状态集合。
 * 终态一经写入不允许回退：迟到 ACK、重复 ACK、超时清扫都只能命中非终态条件。
 */
public final class CommandStatus {

    /** 已受理，指令与 Outbox 已同事务落库。 */
    public static final String ACCEPTED = "ACCEPTED";

    /** 已发 MQ，Broker confirm=ack 且未发生 return。 */
    public static final String SENT_MQ = "SENT_MQ";

    /** 已发终端，字节已写入 Socket，不代表终端已执行。 */
    public static final String SENT_TERMINAL = "SENT_TERMINAL";

    /** 终端已应答，通用 0x0001 结果为成功时的落点。 */
    public static final String ACKED = "ACKED";

    /** 成功，业务应答即成功的指令（如 0x8201 收到 0x0201）直接落这里。 */
    public static final String SUCCESS = "SUCCESS";

    /** 失败，终端明确回失败或发布重试耗尽。 */
    public static final String FAILED = "FAILED";

    /** 超时，超过本次尝试的 expire_at 仍未应答。 */
    public static final String TIMEOUT = "TIMEOUT";

    /** 终态集合：写入后不再接受任何推进。 */
    public static final Set<String> TERMINAL = Set.of(SUCCESS, FAILED, TIMEOUT);

    /**
     * 允许被终端应答推进的来源状态。
     * 含 ACCEPTED：发布确认或状态更新丢失时尝试映射仍存在，ACK 是事实，不能因状态滞后丢弃；
     * 这些都是向前推进，不构成终态回退。ACKED 不在集合内，重复 ACK 自然落空。
     */
    public static final Set<String> ACKABLE = Set.of(ACCEPTED, SENT_MQ, SENT_TERMINAL);

    private CommandStatus() {
    }
}
