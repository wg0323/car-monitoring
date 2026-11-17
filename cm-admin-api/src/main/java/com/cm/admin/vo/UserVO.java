package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户列表行，只返回当前账号数据范围内可见的用户")
public class UserVO {

    @Schema(description = "用户 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "登录名", requiredMode = Schema.RequiredMode.REQUIRED, example = "op1")
    private String username;

    @Schema(description = "用户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "监控员一")
    private String displayName;

    @Schema(description = "是否启用：账号状态非 disabled 即为启用，与登录校验同口径",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean enabled;

    @Schema(description = "手机号，未填写时为空串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNo;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
}
