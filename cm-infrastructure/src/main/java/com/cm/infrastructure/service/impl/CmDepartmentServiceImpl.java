package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmDepartment;
import com.cm.infrastructure.persistence.mapper.CmDepartmentMapper;
import com.cm.infrastructure.persistence.mapper.CmUserDepartmentMapper;
import com.cm.infrastructure.persistence.row.DepartmentDetailRow;
import com.cm.infrastructure.service.CmDepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 部门（车辆分组）持久层实现。树表查询与校验 SQL 由 DepartmentMapper.xml 提供。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmDepartmentServiceImpl extends ServiceImpl<CmDepartmentMapper, CmDepartment>
        implements CmDepartmentService {

    private final CmUserDepartmentMapper userDepartmentMapper;

    private final IdentifierGenerator identifierGenerator;

    @Override
    public List<DepartmentDetailRow> listInScope(DataScope scope, String keyword) {
        requireScope(scope);
        return baseMapper.selectDepartmentsInScope(scope, keyword);
    }

    @Override
    public DepartmentDetailRow detailInScope(DataScope scope, long id) {
        requireScope(scope);
        return baseMapper.selectDepartmentInScope(scope, id);
    }

    @Override
    public boolean existsName(String name, Long excludeId) {
        return baseMapper.countByName(name, excludeId) > 0;
    }

    @Override
    public long countChildren(long id) {
        return baseMapper.countChildren(id);
    }

    @Override
    public long countVehicles(long id) {
        return baseMapper.countVehicles(id);
    }

    @Override
    public List<Long> descendantIds(long id) {
        return baseMapper.selectDescendantIds(id);
    }

    @Override
    public void grantToUser(long userId, long departmentId) {
        long id = identifierGenerator.nextId(null).longValue();
        userDepartmentMapper.grant(id, userId, departmentId);
        log.info("新增分组已授权给创建人, userId={}, departmentId={}", userId, departmentId);
    }

    private void requireScope(DataScope scope) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
    }
}
