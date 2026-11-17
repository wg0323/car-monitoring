package com.cm.infrastructure.persistence.row;

import lombok.Data;

/**
 * 菜单树查询结果行。这是持久层行对象，不是 API 响应 VO。
 */
@Data
public class MenuRow {

    private Long id;

    private String permissionCode;

    private String name;

    private String icon;

    private Integer menuSort;

    private String url;
}
