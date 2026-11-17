package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.UserRoleRow;
import com.cm.infrastructure.persistence.row.UserRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 Mapper。数据范围过滤与角色、部门关联查询写在 mapper/UserMapper.xml。
 * cm_user 没有 department_id 列，用户的数据范围一律按 cm_user_department 关联表用 EXISTS 判定。
 */
public interface CmUserMapper extends BaseMapper<CmUser> {

    long countUserPage(@Param("scope") DataScope scope,
                       @Param("keyword") String keyword,
                       @Param("departmentId") Long departmentId);

    List<UserRow> selectUserPage(@Param("scope") DataScope scope,
                                 @Param("keyword") String keyword,
                                 @Param("departmentId") Long departmentId,
                                 @Param("offset") long offset,
                                 @Param("size") long size);

    /**
     * 按同一份数据范围取单个用户；不在范围内返回 null，调用方据此拒绝，不暴露用户是否存在。
     */
    UserRow selectUserInScope(@Param("scope") DataScope scope,
                              @Param("userId") Long userId);

    List<UserRoleRow> selectUserRoles(@Param("userId") Long userId);

    /**
     * 目标用户的可访问部门，同样按查看者的数据范围裁剪，超管不过滤。
     */
    List<DepartmentRow> selectUserDepartments(@Param("scope") DataScope scope,
                                              @Param("userId") Long userId);
}
