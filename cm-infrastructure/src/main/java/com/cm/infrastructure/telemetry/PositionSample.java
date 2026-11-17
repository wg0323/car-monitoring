package com.cm.infrastructure.telemetry;

/**
 * 位置事件的测量值。字段口径与 JT/T 808 表 20 一致，
 * 里程与电压等附加项缺失时按 0 处理。
 */
public record PositionSample(int alarmFlag, int status, int latitude, int longitude, int altitude,
                             int speed, int course, long mileage, int oil, int recorderSpeed,
                             int signal, int voltage) {
}
