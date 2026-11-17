package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "当前会话：登录用户、权限码快照与可见菜单")
public class SessionVO {

    @Schema(description = "登录用户", requiredMode = Schema.RequiredMode.REQUIRED)
    private LoginUserVO user;

    @Schema(description = "权限码快照，登录时计算，前端菜单与按钮共用", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> permissions;

    @Schema(description = "当前用户可见的侧边菜单，按排序号升序", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MenuVO> menus;
}
