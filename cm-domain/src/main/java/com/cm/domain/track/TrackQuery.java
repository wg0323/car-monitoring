package com.cm.domain.track;

import java.time.LocalDateTime;

/**
 * 单车历史轨迹查询条件。车辆必须已落在登录主体的数据范围内，
 * 查询以接入时刻绑定的 vehicleId 与 simNo 双条件约束，避免 SIM 换绑后串车。
 */
public record TrackQuery(long vehicleId, String simNo, LocalDateTime startTime, LocalDateTime endTime) {
}
