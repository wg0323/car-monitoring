package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 地图区域（电子围栏），对应 cm_map_area；旧表 maparea。
 * 布尔的库列统一为 is_xxx，实体属性不加 is 前缀，避免序列化歧义。
 */
@Data
@TableName("cm_map_area")
public class CmMapArea {

    private Long id;

    private Long companyId;

    private Long departmentId;

    private String name;

    private String areaType;

    private String points;

    private BigDecimal centerLat;

    private BigDecimal centerLng;

    private Integer radius;

    @TableField("is_alarm_enter_driver")
    private Boolean alarmEnterDriver;

    @TableField("is_alarm_enter_platform")
    private Boolean alarmEnterPlatform;

    @TableField("is_alarm_exit_driver")
    private Boolean alarmExitDriver;

    @TableField("is_alarm_exit_platform")
    private Boolean alarmExitPlatform;

    @TableField("is_by_time")
    private Boolean byTime;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @TableField("is_limit_speed")
    private Boolean limitSpeed;

    private BigDecimal maxSpeed;

    private Integer delaySeconds;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
