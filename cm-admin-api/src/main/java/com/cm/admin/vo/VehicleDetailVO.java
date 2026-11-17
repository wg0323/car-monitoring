package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "车辆详情：cm_vehicle 全量基础资料 + 所属部门与终端编号")
public class VehicleDetailVO {

    @Schema(description = "车辆 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String plateNo;

    @Schema(description = "车牌颜色编码，按 JT/T 808 标准：0-蓝 1-黄 2-黑 3-白 4-绿")
    private Integer plateColor;

    @Schema(description = "车牌颜色名称；编码未在标准内时为「其他」")
    private String plateColorName;

    @Schema(description = "所属部门 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    @Schema(description = "所属部门（车组）名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @Schema(description = "所属业户编号，取自所属部门的 member_no")
    private String memberNo;

    @Schema(description = "绑定终端编号")
    private String terminalNo;

    @Schema(description = "终端 SIM 号，库内 11 位口径")
    private String simNo;

    @Schema(description = "车辆类型")
    private String vehicleType;

    @Schema(description = "使用性质")
    private String useType;

    @Schema(description = "所属行业")
    private String industry;

    @Schema(description = "车籍地（行政区划）")
    private String region;

    @Schema(description = "运行状态")
    private String runStatus;

    @Schema(description = "车辆状态")
    private String vehicleStatus;

    @Schema(description = "驾驶员")
    private String driver;

    @Schema(description = "驾驶员电话")
    private String driverMobile;

    @Schema(description = "监控联系人")
    private String monitor;

    @Schema(description = "监控联系人电话")
    private String monitorMobile;

    @Schema(description = "发动机号")
    private String motorId;

    @Schema(description = "发动机编号")
    private String engineNo;

    @Schema(description = "车架号")
    private String frameNo;

    @Schema(description = "制造商")
    private String manufacture;

    @Schema(description = "车辆型号")
    private String modelNo;

    @Schema(description = "服务商")
    private String vendor;

    @Schema(description = "GPS 终端类型")
    private String gpsTerminalType;

    @Schema(description = "道路运输证")
    private String operPermit;

    @Schema(description = "运营线路")
    private String routes;

    @Schema(description = "购置时间")
    private LocalDateTime buyTime;

    @Schema(description = "安装日期")
    private LocalDateTime installDate;

    @Schema(description = "最后检车时间")
    private LocalDateTime lastCheckTime;

    @Schema(description = "最近上线时间")
    private LocalDateTime onlineTime;

    @Schema(description = "最近离线时间")
    private LocalDateTime offlineTime;

    @Schema(description = "服务开始日期")
    private LocalDateTime startDate;

    @Schema(description = "服务结束日期")
    private LocalDateTime endDate;

    @Schema(description = "累计工时")
    private BigDecimal workHour;

    @Schema(description = "备注")
    private String remark;
}
