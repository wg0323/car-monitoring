package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

@Data
@Schema(description = "区域行：几何、报警开关与生效参数")
public class FenceVO {

    @Schema(description = "区域 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属部门 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @Schema(description = "区域名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "大连港圆形区域")
    private String name;

    @Schema(description = "区域类型：circle-圆形，rect-矩形，polygon-多边形", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "circle")
    private String areaType;

    @Schema(description = "边界点串 lng,lat;lng,lat，GCJ-02 坐标；圆形只含圆心", requiredMode = Schema.RequiredMode.REQUIRED)
    private String points;

    @Schema(description = "中心纬度，按边界点外接矩形计算", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double centerLat;

    @Schema(description = "中心经度，按边界点外接矩形计算", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double centerLng;

    @Schema(description = "圆形半径（米），非圆形为 0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer radius;

    @Schema(description = "进入区域报警给驾驶员", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alarmEnterDriver;

    @Schema(description = "进入区域报警给平台", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alarmEnterPlatform;

    @Schema(description = "离开区域报警给驾驶员", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alarmExitDriver;

    @Schema(description = "离开区域报警给平台", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alarmExitPlatform;

    @Schema(description = "是否按时间段生效", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean byTime;

    @Schema(description = "生效开始时间，格式 yyyy-MM-dd HH:mm:ss；未启用时间段时为 null",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String startTimeText;

    @Schema(description = "生效结束时间，格式 yyyy-MM-dd HH:mm:ss；未启用时间段时为 null",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String endTimeText;

    @Schema(description = "是否启用区域限速", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean limitSpeed;

    @Schema(description = "区域限速值（km/h），未启用限速时为 0", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal maxSpeed;

    @Schema(description = "延时报警（秒）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer delaySeconds;

    @Schema(description = "备注", requiredMode = Schema.RequiredMode.REQUIRED)
    private String remark;

    @Schema(description = "创建时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createTimeText;

    @Schema(description = "最后修改时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String updateTimeText;
}
