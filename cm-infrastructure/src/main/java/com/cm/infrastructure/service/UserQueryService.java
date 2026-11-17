package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.UserRoleRow;
import com.cm.infrastructure.persistence.row.UserRow;

import java.util.List;

/**
 * 用户查询。列表与详情共用同一份服务端数据范围：
 * 被查看用户的可访问部门必须与 DataScope.departmentIds 有交集，超管跳过滤。
 */
public interface UserQueryService {

    IPage<UserRow> pageByScope(DataScope scope, long pageNo, long pageSize, String keyword, Long departmentId);

    /**
     * 按同一份数据范围取单个用户，不在范围内返回 null。调用方必须据此拒绝，不得当成「用户不存在」放行。
     */
    UserRow findInScope(DataScope scope, Long userId);

    List<UserRoleRow> roles(Long userId);

    /**
     * 目标用户的可访问部门；同样按查看者的数据范围裁剪，超管返回全量。
     */
    List<DepartmentRow> departments(DataScope scope, Long userId);
}
