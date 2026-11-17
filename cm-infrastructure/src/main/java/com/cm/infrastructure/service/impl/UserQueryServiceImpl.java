package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.UserRoleRow;
import com.cm.infrastructure.persistence.row.UserRow;
import com.cm.infrastructure.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户查询实现。范围条件全部在 UserMapper.xml 的 userScopeFilter 里，本类不拼 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final CmUserMapper userMapper;

    @Override
    public IPage<UserRow> pageByScope(DataScope scope, long pageNo, long pageSize, String keyword,
                                      Long departmentId) {
        requireScope(scope);
        long offset = computeOffset(pageNo, pageSize);
        long total = userMapper.countUserPage(scope, keyword, departmentId);
        List<UserRow> rows = userMapper.selectUserPage(scope, keyword, departmentId, offset, pageSize);
        Page<UserRow> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public UserRow findInScope(DataScope scope, Long userId) {
        requireScope(scope);
        if (userId == null) {
            return null;
        }
        return userMapper.selectUserInScope(scope, userId);
    }

    @Override
    public List<UserRoleRow> roles(Long userId) {
        return userMapper.selectUserRoles(userId);
    }

    @Override
    public List<DepartmentRow> departments(DataScope scope, Long userId) {
        requireScope(scope);
        return userMapper.selectUserDepartments(scope, userId);
    }

    private void requireScope(DataScope scope) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
    }

    private long computeOffset(long pageNo, long pageSize) {
        if (pageNo < 1 || pageNo > QueryLimits.MAX_PAGE_NO
                || pageSize < 1 || pageSize > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("分页参数超出允许范围");
        }
        try {
            return Math.multiplyExact(pageNo - 1, pageSize);
        } catch (ArithmeticException ex) {
            throw new CmBusinessException("分页偏移量溢出", ex);
        }
    }
}
