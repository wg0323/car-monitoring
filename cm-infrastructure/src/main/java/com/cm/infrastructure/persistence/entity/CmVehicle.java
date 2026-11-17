package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆基础资料，对应 cm_vehicle；旧表 vehicle。
 */
@Data
@TableName("cm_vehicle")
public class CmVehicle {

    private Long id;

    private Long companyId;

    private Long departmentId;

    private Long terminalId;

    private String plateNo;

    private Integer plateColor;

    private String simNo;

    private String vehicleType;

    private String useType;

    private String industry;

    private String region;

    private String driver;

    private String driverMobile;

    private String monitor;

    private String monitorMobile;

    private String motorId;

    private String engineNo;

    private String frameNo;

    private String manufacture;

    private String modelNo;

    private String vendor;

    private String gpsTerminalType;

    private String videoDeviceId;

    private String operPermit;

    private String routes;

    private String runStatus;

    private String vehicleStatus;

    private LocalDateTime buyTime;

    private LocalDateTime installDate;

    private LocalDateTime lastCheckTime;

    private LocalDateTime onlineTime;

    private LocalDateTime offlineTime;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private BigDecimal workHour;

    private String photoUrl;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
