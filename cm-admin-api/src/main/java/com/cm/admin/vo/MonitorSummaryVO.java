package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "实时监控状态栏：按当前账号数据范围统计")
public class MonitorSummaryVO {

    @Schema(description = "范围内车辆总数，与车辆列表 total 同一口径", requiredMode = Schema.RequiredMode.REQUIRED)
    private long total;

    @Schema(description = "在线车辆数，按 Redis 在线租约统计", requiredMode = Schema.RequiredMode.REQUIRED)
    private long online;

    @Schema(description = "离线车辆数 = 总数 - 在线数", requiredMode = Schema.RequiredMode.REQUIRED)
    private long offline;

    @Schema(description = "停车数：在线且最新速度为零；位置过期取不到速度的在线车不计入",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long parking;

    @Schema(description = "上线率（百分比，保留 2 位小数）；总数为 0 时为 0", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "0.01")
    private double onlineRate;
}
