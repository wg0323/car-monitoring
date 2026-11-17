package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆详情行。对应旧系统 viewVehicle 的两块内容：车辆基础资料与驾驶员/监控联系人，
 * 旧 driverInfo 明细表未迁移，这里只取 cm_vehicle 自身字段。
 */
@Data
public class VehicleDetailRow {

    private Long id;

    private String plateNo;

    private Integer plateColor;

    private Long departmentId;

    private String departmentName;

    /** 部门的车组业户编号，对齐旧弹窗的「所属业户」。 */
    private String memberNo;

    private String terminalNo;

    private String simNo;

    private String vehicleType;

    private String useType;

    private String industry;

    private String region;

    private String runStatus;

    private String vehicleStatus;

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

    private String operPermit;

    private String routes;

    private LocalDateTime buyTime;

    private LocalDateTime installDate;

    private LocalDateTime lastCheckTime;

    private LocalDateTime onlineTime;

    private LocalDateTime offlineTime;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private BigDecimal workHour;

    private String remark;
}
