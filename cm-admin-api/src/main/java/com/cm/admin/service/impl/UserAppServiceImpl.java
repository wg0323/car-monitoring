package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.ViewConverter;
import com.cm.admin.service.UserAppService;
import com.cm.admin.vo.UserDetailVO;
import com.cm.admin.vo.UserVO;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.UserRow;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户列表与详情实现。
 * 范围口径：被查看用户的可访问部门必须与当前账号 DataScope.departmentIds 有交集（超管全量），
 * 列表的 departmentId 筛选只是在这份范围之上再等值收窄，不是权限参数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAppServiceImpl implements UserAppService {

    private final UserQueryService userQueryService;

    private final DataScopeService dataScopeService;

    @Override
    public PageResult<UserVO> page(Long userId, long pageNo, long pageSize, String keyword, Long departmentId) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<UserRow> page = userQueryService.pageByScope(scope, pageNo, pageSize, keyword, departmentId);
        List<UserVO> records = page.getRecords().stream()
                .map(ViewConverter::toUserVO)
                .toList();
        log.debug("用户列表, userId={}, total={}, size={}", userId, page.getTotal(), records.size());
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public UserDetailVO detail(Long userId, Long targetUserId) {
        DataScope scope = dataScopeService.resolve(userId);
        UserRow row = userQueryService.findInScope(scope, targetUserId);
        if (row == null) {
            // 不区分「不存在」与「越界」：两种都拒绝，避免用 id 探测他人账号是否存在。
            throw new AccessDeniedException("目标用户不在当前账号的数据范围内");
        }
        log.debug("用户详情, userId={}, targetUserId={}", userId, targetUserId);
        return ViewConverter.toUserDetailVO(row,
                userQueryService.roles(targetUserId),
                userQueryService.departments(scope, targetUserId));
    }

    /**
     * 部门筛选值越界即拒绝，与车辆列表同一口径：请求值只与数据范围比对，不合并进权限参数。
     */
    private void assertDepartmentInScope(DataScope scope, Long departmentId) {
        if (departmentId == null || scope.isSuperAdmin()) {
            return;
        }
        Set<Long> allowed = new HashSet<>(scope.getDepartmentIds());
        if (!allowed.contains(departmentId)) {
            throw new AccessDeniedException("所选部门不在当前账号的数据范围内");
        }
    }
}
