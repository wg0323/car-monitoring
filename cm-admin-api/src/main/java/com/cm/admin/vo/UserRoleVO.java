package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "用户所属角色")
public class UserRoleVO {

    @Schema(description = "角色 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    @Schema(description = "角色编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "ROLE_MONITOR")
    private String roleCode;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "监控员（受限数据范围）")
    private String roleName;
}
