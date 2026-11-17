package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "地图配置。key 由服务端从环境变量下发，不入仓库也不进前端构建产物")
public class MonitorMapConfigVO {

    @Schema(description = "是否已配置地图 key；false 时前端不得加载地图 SDK", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean configured;

    @Schema(description = "地图引擎标识，当前固定 amap（高德 JS API 2.0）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "amap")
    private String engine;

    @Schema(description = "地图 key，未配置时为空串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @Schema(description = "高德安全密钥，未配置时为空串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String securityCode;

    @Schema(description = "初始中心纬度，取自 cm_system_config.initLat", requiredMode = Schema.RequiredMode.REQUIRED)
    private double centerLat;

    @Schema(description = "初始中心经度，取自 cm_system_config.initLng", requiredMode = Schema.RequiredMode.REQUIRED)
    private double centerLng;

    @Schema(description = "初始缩放级别，取自 cm_system_config.initZoomLevel", requiredMode = Schema.RequiredMode.REQUIRED)
    private int centerZoom;
}
