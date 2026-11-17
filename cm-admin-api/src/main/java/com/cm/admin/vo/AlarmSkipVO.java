package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量处理中被跳过的一行。旧系统只回一句「以下报警记录不存在」，
 * 这里按 ID 给原因，页面可以直接展示哪几条没处理以及为什么。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量处理跳过项：报警 ID 与跳过原因")
public class AlarmSkipVO {

    @Schema(description = "被跳过的报警 ID，字符串形式", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "跳过原因", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "报警不存在、已处理或不在可访问范围内")
    private String reason;
}
