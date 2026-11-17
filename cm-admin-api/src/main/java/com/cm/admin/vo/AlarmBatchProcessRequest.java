package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量处理请求体。ID 用字符串接收再解析：19 位雪花在 JavaScript 里已经丢精度，
 * 前端一律按字符串传，服务端自己校验格式。
 */
@Data
@Schema(description = "报警批量处理请求：勾选行 ID 与统一备注")
public class AlarmBatchProcessRequest {

    @Schema(description = "报警记录 ID 列表，字符串形式，单次上限 100 条", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请选择要处理的报警记录")
    private List<String> ids;

    @Schema(description = "处理备注，可为空；超过 255 字符拒绝")
    @Size(max = 255, message = "处理备注不能超过 255 字符")
    private String remark;
}
