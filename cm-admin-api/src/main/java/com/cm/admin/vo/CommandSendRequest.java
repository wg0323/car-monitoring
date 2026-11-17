package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "点名（0x8201 位置信息查询）请求体")
public class CommandSendRequest {

    @Schema(description = "目标车辆 ID，必须在当前账号数据范围内且终端在线",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "100000000000000001")
    @NotNull(message = "请选择目标车辆")
    private Long vehicleId;
}
