package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 区域列表与详情的查询结果行。这是持久层行对象，不是 API 响应 VO。
 */
@Data
public class MapAreaRow {

    private Long id;

    private Long departmentId;

    private String departmentName;

    private String name;

    private String areaType;

    private String points;

    private BigDecimal centerLat;

    private BigDecimal centerLng;

    private Integer radius;

    private Boolean alarmEnterDriver;

    private Boolean alarmEnterPlatform;

    private Boolean alarmExitDriver;

    private Boolean alarmExitPlatform;

    private Boolean byTime;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean limitSpeed;

    private BigDecimal maxSpeed;

    private Integer delaySeconds;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
