package com.cm.infrastructure.telemetry;

import java.time.Instant;

/**
 * 历史轨迹点。经纬度沿用 808 的百万分之一度整数，速度与记录仪速度为 0.1 公里/小时，
 * 换算与展示口径由 API 层统一处理。
 */
public record TrackPoint(String eventId, Instant deviceTime, int latitude, int longitude, int altitude,
                         int speed, int recorderSpeed, int course, long mileage, int oil,
                         int status, int alarmFlag) {
}
