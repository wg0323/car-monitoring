package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED, example = "sa")
    @NotBlank(message = "登录名不能为空")
    private String username;

    @Schema(description = "登录口令", requiredMode = Schema.RequiredMode.REQUIRED, example = "123321")
    @NotBlank(message = "口令不能为空")
    private String password;
}
