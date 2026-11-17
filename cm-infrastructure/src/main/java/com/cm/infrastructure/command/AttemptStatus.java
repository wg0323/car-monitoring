package com.cm.infrastructure.command;

/**
 * cm_command_attempt.write_status 的取值。
 * PERSISTED 在调用 writeAndFlush 之前写入，保证「先持久化映射再写帧」；
 * 只有 WRITTEN 才允许把指令推进到已发终端。
 */
public final class AttemptStatus {

    /** 映射已落库，尚未确认写入 Socket。 */
    public static final String PERSISTED = "PERSISTED";

    /** 帧已成功写入 Socket。 */
    public static final String WRITTEN = "WRITTEN";

    /** 写失败，通道异常或 future 失败。 */
    public static final String FAILED = "FAILED";

    /** 未写，目标 SIM 当前无可用连接。 */
    public static final String DROPPED = "DROPPED";

    private AttemptStatus() {
    }
}
