package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前在线报表的车辆候选行。在线态与离线时长来自 Redis 在线租约与最新位置，不在 SQL 里判定。
 */
@Data
public class VehicleOnlineReportRow {

    private Long id;

    private String plateNo;

    private Integer plateColor;

    private String simNo;

    private String departmentName;

    private String runStatus;

    private String vehicleType;

    private LocalDateTime installDate;
}
