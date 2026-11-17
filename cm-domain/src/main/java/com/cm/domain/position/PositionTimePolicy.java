package com.cm.domain.position;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 设备时间质量判定。有效设备时间取 min(设备时间, 接入时间)：
 * 设备时钟超前时不让未来时间霸占最新位置，时钟落后时仍按真实设备时间排序，
 * 避免迟到数据被误抬成最新。
 */
public final class PositionTimePolicy {

    private final Duration futureTolerance;

    private final Duration staleThreshold;

    private final Duration driftTolerance;

    private final ZoneId zone;

    public PositionTimePolicy(Duration futureTolerance, Duration staleThreshold, Duration driftTolerance,
            ZoneId zone) {
        this.futureTolerance = Objects.requireNonNull(futureTolerance, "futureTolerance");
        this.staleThreshold = Objects.requireNonNull(staleThreshold, "staleThreshold");
        this.driftTolerance = Objects.requireNonNull(driftTolerance, "driftTolerance");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public PositionTimeVerdict evaluate(LocalDateTime deviceTime, Instant ingestTime) {
        Objects.requireNonNull(deviceTime, "deviceTime");
        Objects.requireNonNull(ingestTime, "ingestTime");
        Instant deviceInstant = deviceTime.atZone(zone).toInstant();
        long offsetMillis = deviceInstant.toEpochMilli() - ingestTime.toEpochMilli();
        Instant effective = deviceInstant.isAfter(ingestTime) ? ingestTime : deviceInstant;
        if (offsetMillis > futureTolerance.toMillis()) {
            return new PositionTimeVerdict(TimeQuality.FUTURE, effective);
        }
        if (offsetMillis < -staleThreshold.toMillis()) {
            return new PositionTimeVerdict(TimeQuality.STALE, effective);
        }
        if (Math.abs(offsetMillis) > driftTolerance.toMillis()) {
            return new PositionTimeVerdict(TimeQuality.CLOCK_DRIFT, effective);
        }
        return new PositionTimeVerdict(TimeQuality.NORMAL, effective);
    }
}
