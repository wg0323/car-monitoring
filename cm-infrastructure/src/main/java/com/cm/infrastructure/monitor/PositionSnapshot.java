package com.cm.infrastructure.monitor;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;

/**
 * 实时监控页需要的定位快照。经纬度沿用 808 的百万分之一度整数口径，由 API 层换算成小数。
 */
public record PositionSnapshot(int latitude, int longitude, int speed, int course, int alarmFlag, int status,
                               String deviceTime) {

    public static PositionSnapshot of(PositionEvent event) {
        PositionSample sample = event.sample();
        return new PositionSnapshot(sample.latitude(), sample.longitude(), sample.speed(), sample.course(),
                sample.alarmFlag(), sample.status(), event.deviceTime());
    }
}
