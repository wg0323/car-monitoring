package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "当前登录用户")
public class LoginUserVO {

    @Schema(description = "用户 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED, example = "sa")
    private String username;

    @Schema(description = "显示名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(description = "是否超管：true 时服务端不做部门数据范围过滤", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean superAdmin;
}
