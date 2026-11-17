package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "新建或修改区域的请求体。几何由地图绘制产生，校验规则与旧系统 saveArea 一致")
public class FenceSaveRequest {

    @Schema(description = "区域名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "大连港圆形区域")
    @NotBlank(message = "区域名称不能为空")
    @Size(max = 64, message = "区域名称不能超过 64 个字符")
    private String name;

    @Schema(description = "所属部门 ID，必须在当前账号数据范围内", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择所属部门")
    private Long departmentId;

    @Schema(description = "区域类型：circle-圆形，rect-矩形，polygon-多边形",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "circle")
    @NotBlank(message = "区域类型不能为空")
    @Pattern(regexp = "circle|rect|polygon", message = "区域类型只支持 circle / rect / polygon")
    private String areaType;

    @Schema(description = "边界点串 lng,lat;lng,lat，GCJ-02 坐标；圆形只传圆心，矩形四个角点，多边形至少三点",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "121.500000,31.200000")
    @NotBlank(message = "区域边界点不能为空")
    @Size(max = 4000, message = "区域边界点串过长")
    private String points;

    @Schema(description = "圆形半径（米），圆形必填；矩形与多边形忽略", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "800")
    private Integer radius;

    @Schema(description = "进入区域报警给驾驶员", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean alarmEnterDriver;

    @Schema(description = "进入区域报警给平台", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean alarmEnterPlatform;

    @Schema(description = "离开区域报警给驾驶员", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean alarmExitDriver;

    @Schema(description = "离开区域报警给平台", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean alarmExitPlatform;

    @Schema(description = "是否按时间段生效；为 true 时起止时间必填", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean byTime;

    @Schema(description = "生效开始时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "2026-11-17 08:00:00")
    private String startDate;

    @Schema(description = "生效结束时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "2026-11-17 18:00:00")
    private String endDate;

    @Schema(description = "是否启用区域限速；为 true 时限速值必填", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean limitSpeed;

    @Schema(description = "区域限速值（km/h）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "80")
    private BigDecimal maxSpeed;

    @Schema(description = "延时报警（秒），0~3600", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "30")
    private Integer delaySeconds;

    @Schema(description = "备注", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
