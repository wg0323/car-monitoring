package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/**
 * 报警通知行。底部「报警数据」与右下角报警弹窗共用一份结构。
 */
@Data
@Schema(description = "报警通知行：底部报警数据与报警弹窗共用")
public class AlarmNotificationVO {

    @Schema(description = "报警记录 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "甘A43060")
    private String plateNo;

    @Schema(description = "车牌颜色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "蓝色")
    private String plateColorName;

    @Schema(description = "SIM 号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @Schema(description = "报警类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "超速报警")
    private String alarmTypeName;

    @Schema(description = "报警来源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "终端报警")
    private String alarmSourceName;

    @Schema(description = "报警地点描述；未接逆地理编码时为空串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String location;

    @Schema(description = "报警开始时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startTimeText;

    @Schema(description = "报警是否已处理", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean processed;

    @Schema(description = "车辆 ID，序列化为字符串避免前端精度丢失")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long vehicleId;

    @Schema(description = "报警点纬度（WGS-84）")
    private BigDecimal latitude;

    @Schema(description = "报警点经度（WGS-84）")
    private BigDecimal longitude;
}
