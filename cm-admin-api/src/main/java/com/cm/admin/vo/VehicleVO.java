package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "车辆列表行")
public class VehicleVO {

    @Schema(description = "车辆 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @Schema(description = "终端 SIM 号，库内 11 位口径", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @Schema(description = "所属部门 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @Schema(description = "车辆类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String vehicleType;

    @Schema(description = "绑定终端编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String terminalNo;

    @Schema(description = "驾驶员", requiredMode = Schema.RequiredMode.REQUIRED)
    private String driver;

    @Schema(description = "运行状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String runStatus;
}
