package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "809 链路命令请求体，只接受旧系统监管命令页实测的四条命令")
public class LinkCommandRequest {

    @Schema(description = "链路命令：0x1001-连接主链路，0x1003-主链路注销，"
            + "0x1007-主链路断开通知，0x1008-主动关闭主从链路通知",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "4097")
    @NotNull(message = "链路命令不能为空")
    private Integer linkCmd;
}
