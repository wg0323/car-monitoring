package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "809 报警督办应答请求")
public class UrgeTodoAckRequest {

    @NotNull
    @Min(0)
    @Max(3)
    @Schema(description = "处理结果码：0 处理中、1 已处理完毕、2 不做处理、3 将来处理",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer result;
}
