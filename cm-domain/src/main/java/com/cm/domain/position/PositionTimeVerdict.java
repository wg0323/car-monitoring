package com.cm.domain.position;

import java.time.Instant;
import java.util.Objects;

/**
 * 时间判定结果。effectiveDeviceTime 参与最新位置排序，
 * 与原始 deviceTime 分开保存，便于排查时钟漂移。
 */
public record PositionTimeVerdict(TimeQuality quality, Instant effectiveDeviceTime) {

    public PositionTimeVerdict {
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(effectiveDeviceTime, "effectiveDeviceTime");
    }

    /**
     * 严重异常时间不入主集合、不更新最新位置。
     */
    public boolean quarantined() {
        return quality == TimeQuality.FUTURE || quality == TimeQuality.STALE;
    }
}
