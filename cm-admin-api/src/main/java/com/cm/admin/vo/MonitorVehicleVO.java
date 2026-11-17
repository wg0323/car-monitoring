package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "实时监控行：车辆基础信息 + 最新位置 + 在线态")
public class MonitorVehicleVO {

    @Schema(description = "车辆 ID，序列化为字符串避免前端精度丢失，也是 WebSocket 订阅用的键",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @Schema(description = "终端 SIM 号，库内 11 位口径", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @Schema(description = "是否在线：以 Redis 在线租约为准，租约过期即离线", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean online;

    @Schema(description = "最新纬度（度）。无定位或位置已过期时为 null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Double latitude;

    @Schema(description = "最新经度（度）。无定位或位置已过期时为 null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Double longitude;

    @Schema(description = "最新速度（公里/小时），由 808 的 0.1 公里/小时换算。无定位时为 null",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Double speedKph;

    @Schema(description = "最新方向（度，0 为正北，顺时针）。无定位时为 null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer course;

    @Schema(description = "最新定位的设备时间，ISO-8601 字符串。无定位时为 null", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String deviceTime;
}
