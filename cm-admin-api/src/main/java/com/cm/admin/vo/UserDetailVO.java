package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "用户详情：基本信息、角色列表与可访问部门")
public class UserDetailVO {

    @Schema(description = "用户 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED, example = "op1")
    private String username;

    @Schema(description = "用户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "监控员一")
    private String displayName;

    @Schema(description = "用户类型：admin-超管，operation-运营，monitor-监控", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userType;

    @Schema(description = "是否启用：账号状态非 disabled 即为启用，与登录校验同口径",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean enabled;

    @Schema(description = "手机号，未填写时为空串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNo;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "角色列表，未分配角色时为空数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<UserRoleVO> roles;

    @Schema(description = "可访问部门，即 cm_user_department 中的授权部门", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DepartmentVO> departments;
}
