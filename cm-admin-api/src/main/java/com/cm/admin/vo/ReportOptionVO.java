package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "报表筛选项")
public class ReportOptionVO {

    @Schema(description = "选项编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private String code;

    @Schema(description = "选项名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "超速报警")
    private String name;
}
