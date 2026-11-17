package com.cm.domain.position;

/**
 * 设备时间质量档位。网关按设备时间与接入时间的偏差判定，
 * 异常时间据此隔离或按接入时间降级排序。
 */
public enum TimeQuality {

    /** 偏差在容忍范围内，正常入库。 */
    NORMAL,

    /** 偏差超过漂移阈值但仍在可接受窗口内，数据可用但需标记。 */
    CLOCK_DRIFT,

    /** 设备时间明显超前，隔离处理。 */
    FUTURE,

    /** 设备时间过老，隔离处理。 */
    STALE
}
