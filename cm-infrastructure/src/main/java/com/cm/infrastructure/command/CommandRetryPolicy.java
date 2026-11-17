package com.cm.infrastructure.command;

/**
 * 发布失败的重试节奏：指数退避、封顶、有上限。
 * 重试沿用同一 commandId，只增加发布尝试次数，不生成新业务键。
 */
public final class CommandRetryPolicy {

    private static final int MAX_SHIFT = 16;

    private CommandRetryPolicy() {
    }

    /**
     * 第 attempt 次发布失败后的等待时长，attempt 从 1 开始。
     */
    public static long backoffMillis(int attempt, long baseMillis, long maxMillis) {
        int steps = Math.max(attempt - 1, 0);
        long factor = 1L << Math.min(steps, MAX_SHIFT);
        return Math.min(baseMillis * factor, maxMillis);
    }
}
