package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "CSRF 令牌元信息。令牌值本身由服务端写入 XSRF-TOKEN cookie，前端从 cookie 读取后放请求头")
public class CsrfTokenVO {

    @Schema(description = "请求头名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "X-XSRF-TOKEN")
    private String headerName;

    @Schema(description = "表单参数名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "_csrf")
    private String parameterName;
}
