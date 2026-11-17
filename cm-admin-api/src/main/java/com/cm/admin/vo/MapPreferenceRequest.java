package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "保存当前账号默认地图中心的请求体")
public class MapPreferenceRequest {

    @Schema(description = "中心纬度（GCJ-02 口径，与底图一致）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "38.914004")
    @NotNull(message = "中心纬度不能为空")
    @DecimalMin(value = "-90", message = "中心纬度超出范围")
    @DecimalMax(value = "90", message = "中心纬度超出范围")
    private Double centerLat;

    @Schema(description = "中心经度（GCJ-02 口径，与底图一致）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "121.614682")
    @NotNull(message = "中心经度不能为空")
    @DecimalMin(value = "-180", message = "中心经度超出范围")
    @DecimalMax(value = "180", message = "中心经度超出范围")
    private Double centerLng;

    @Schema(description = "缩放级别，高德 JS API 2.0 支持 3~20", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "12")
    @NotNull(message = "缩放级别不能为空")
    @Min(value = 3, message = "缩放级别不能小于 3")
    @Max(value = 20, message = "缩放级别不能大于 20")
    private Integer zoom;
}
