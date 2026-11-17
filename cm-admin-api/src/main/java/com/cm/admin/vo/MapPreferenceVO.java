package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "当前账号已保存的地图中心")
public class MapPreferenceVO {

    @Schema(description = "中心纬度（GCJ-02 口径）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double centerLat;

    @Schema(description = "中心经度（GCJ-02 口径）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double centerLng;

    @Schema(description = "缩放级别", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer zoom;

    @Schema(description = "是否由用户保存过；false 表示当前用的是系统默认中心", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean saved;
}
