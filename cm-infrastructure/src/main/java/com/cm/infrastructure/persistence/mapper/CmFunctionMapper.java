package com.cm.infrastructure.persistence.mapper;

import com.cm.infrastructure.persistence.row.MenuRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单与功能权限的命名查询。SQL 写在 mapper/FunctionMapper.xml。
 * 该表由 P2 建表、P4 分来源，因此不建 Entity：读写都经显式列，避免 select * 带出 100 行旧功能的全部字段。
 */
public interface CmFunctionMapper {

    List<MenuRow> selectMenuTree();

    List<String> selectPermissionCodesByUser(@Param("userId") Long userId);

    List<String> selectAllPermissionCodes();
}
