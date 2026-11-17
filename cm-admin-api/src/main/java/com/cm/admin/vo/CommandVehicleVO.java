package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "可下发指令的车辆候选行")
public class CommandVehicleVO {

    @Schema(description = "车辆 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @Schema(description = "终端 SIM 号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @Schema(description = "是否在线：以 Redis 在线租约为准，离线车辆不能下发即时指令",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean online;
}
