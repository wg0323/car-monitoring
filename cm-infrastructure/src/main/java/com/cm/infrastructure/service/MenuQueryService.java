package com.cm.infrastructure.service;

import com.cm.infrastructure.persistence.row.MenuRow;

import java.util.List;

/**
 * 菜单与功能权限查询。登录时各取一次，结果快照进会话，不在每个请求上重复查库。
 */
public interface MenuQueryService {

    /**
     * 侧边导航使用的九域菜单，只含 P4 新菜单，不含旧功能表装载的地图工具与命令面板。
     */
    List<MenuRow> menuTree();

    /**
     * 用户的权限码集合。超管取全量，普通用户按角色授权取。
     */
    List<String> permissionCodes(Long userId, boolean superAdmin);
}
