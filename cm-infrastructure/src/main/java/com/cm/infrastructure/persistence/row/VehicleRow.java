package com.cm.infrastructure.persistence.row;

import lombok.Data;

/**
 * 车辆列表与车辆树的查询结果行。这是持久层行对象，不是 API 响应 VO。
 */
@Data
public class VehicleRow {

    private Long id;

    private String plateNo;

    /** 只有按 ID 查范围内车辆时回填，车辆列表不取该列。 */
    private Integer plateColor;

    private String simNo;

    private String departmentName;

    private String vehicleType;

    private String terminalNo;

    private String driver;

    private String runStatus;

    private Long companyId;

    private Long departmentId;
}
