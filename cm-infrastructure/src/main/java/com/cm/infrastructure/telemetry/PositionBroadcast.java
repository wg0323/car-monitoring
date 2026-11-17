package com.cm.infrastructure.telemetry;

/**
 * 实时广播的精简定位事件。不复用完整 {@link PositionEvent}：那里还有 eventId、payloadDigest、traceId
 * 等排障字段，每秒数百条地投到广播通道只会放大 Redis 与浏览器的流量。
 */
public record PositionBroadcast(int schemaVersion, String simNo, int latitude, int longitude, int speed,
                                int course, int alarmFlag, int status, String deviceTime) {

    public static final int SCHEMA_VERSION = 1;

    public static PositionBroadcast of(PositionEvent event) {
        PositionSample sample = event.sample();
        return new PositionBroadcast(SCHEMA_VERSION, event.simNo(), sample.latitude(), sample.longitude(),
                sample.speed(), sample.course(), sample.alarmFlag(), sample.status(), event.deviceTime());
    }
}
