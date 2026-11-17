package com.cm.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "统一分页响应")
public class PageResult<T> {

    @Schema(description = "满足查询条件的总行数，服务端已按数据范围过滤", requiredMode = Schema.RequiredMode.REQUIRED, example = "10003")
    private long total;

    @Schema(description = "当前页码，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private long page;

    @Schema(description = "每页条数", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private long size;

    @Schema(description = "当前页数据", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> records;
}
