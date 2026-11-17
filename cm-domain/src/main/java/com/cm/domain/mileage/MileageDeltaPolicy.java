package com.cm.domain.mileage;

/**
 * 累计里程差值策略。0x01 附加项是终端上报的累计里程，单位 0.1 km（旧码 {@code 0.1 * mileage}）。
 * 四种边界必须显式区分：缺附加项、无基线、设备累计值归零（换机或重启）、单点跳变过大。
 * 除 NORMAL 外增量一律为 0，不允许负增量或凭空多出来的里程。
 */
public final class MileageDeltaPolicy {

    /** 原始值单位是 0.1 km，每公里 10 个单位。 */
    public static final long RAW_PER_KILOMETER = 10L;

    private MileageDeltaPolicy() {
    }

    public enum Status {
        /** 正常增量。 */
        NORMAL,
        /** 首次见到该终端，当前值只能作基线。 */
        BASELINE,
        /** 累计值比上次小，除换机或恢复出厂，按重置处理；否则历史数据会凭空抖动。 */
        RESET,
        /** 单点增量超过阈值，按脏数据处理。 */
        ABNORMAL,
        /** 报文未带 0x01 附加项。 */
        MISSING
    }

    public record Delta(long deltaRaw, Status status) {

        /**
         * 增量为 0 的正常样本（重复投递同一帧）不算「计入里程」，否则指标会把重复投递当成贡献。
         */
        public boolean counted() {
            return status == Status.NORMAL && deltaRaw > 0L;
        }
    }

    /**
     * @param lastRaw    上次累计值，未知时传 0 或负数
     * @param currentRaw 本次累计值
     * @param maxDeltaRaw 单点增量上限（原始单位）
     */
    public static Delta evaluate(long lastRaw, long currentRaw, long maxDeltaRaw) {
        if (currentRaw <= 0) {
            return new Delta(0L, Status.MISSING);
        }
        if (lastRaw <= 0) {
            return new Delta(0L, Status.BASELINE);
        }
        if (currentRaw < lastRaw) {
            return new Delta(0L, Status.RESET);
        }
        long delta = currentRaw - lastRaw;
        if (delta > maxDeltaRaw) {
            return new Delta(0L, Status.ABNORMAL);
        }
        return new Delta(delta, Status.NORMAL);
    }
}
