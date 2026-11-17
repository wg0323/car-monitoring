package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.entity.CmUserDepartment;
import com.cm.infrastructure.persistence.mapper.CmUserDepartmentMapper;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import com.cm.infrastructure.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 数据范围按用户类型与 user_department 关联表服务端生成，修复旧系统客户端可自带权限参数的缺陷。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private static final String DISABLED_STATE = "disabled";

    private final CmUserMapper userMapper;

    private final CmUserDepartmentMapper userDepartmentMapper;

    @Override
    public DataScope resolve(Long userId) {
        if (userId == null) {
            throw new CmBusinessException("未认证的主体不能构造数据范围");
        }
        CmUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new CmBusinessException("用户不存在");
        }
        if (DISABLED_STATE.equalsIgnoreCase(user.getUserState())) {
            throw new CmBusinessException("账号已禁用");
        }
        if (user.getCompanyId() == null) {
            throw new CmBusinessException("用户未配置所属企业");
        }
        if (DataScope.isAdminUserType(user.getUserType())) {
            log.debug("超管数据范围, userId={}", userId);
            return DataScope.superAdmin(userId);
        }
        List<Long> departmentIds = userDepartmentMapper
                .selectList(new LambdaQueryWrapper<CmUserDepartment>()
                        .eq(CmUserDepartment::getUserId, userId)
                        .eq(CmUserDepartment::getDeleted, false))
                .stream()
                .map(CmUserDepartment::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        log.debug("普通用户数据范围, userId={}, departmentCount={}", userId, departmentIds.size());
        return new DataScope(userId, false, List.of(user.getCompanyId()), departmentIds);
    }
}
