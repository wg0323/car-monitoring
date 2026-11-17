package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 单车轨迹查询结果。页面表格、地图折线与回放共用这一份点集，
 * 导出走同一查询条件，因此 total 与导出行数天然一致。
 */
@Data
@Schema(description = "历史轨迹查询结果")
public class TrackResultVO {

    @Schema(description = "车辆 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long vehicleId;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @Schema(description = "终端 SIM 号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @Schema(description = "轨迹点总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8640")
    private long total;

    @Schema(description = "按设备时间升序的轨迹点", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TrackPointVO> points;
}
