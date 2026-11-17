package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "侧边导航菜单项")
public class MenuVO {

    @Schema(description = "权限码，前端路由与按钮共用同一套判断", requiredMode = Schema.RequiredMode.REQUIRED, example = "vehicle:view")
    private String permissionCode;

    @Schema(description = "菜单名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "车辆与终端")
    private String name;

    @Schema(description = "SVG 图标标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "vehicle")
    private String icon;

    @Schema(description = "排序号", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private Integer menuSort;

    @Schema(description = "前端路由路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "/vehicle")
    private String url;
}
