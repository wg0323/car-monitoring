package com.cm.admin.service.impl;

import com.cm.admin.converter.DepartmentConverter;
import com.cm.admin.converter.ViewConverter;
import com.cm.admin.service.DepartmentAppService;
import com.cm.admin.vo.DepartmentDetailVO;
import com.cm.admin.vo.DepartmentSaveRequest;
import com.cm.admin.vo.DepartmentVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.exception.CmConflictException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmDepartment;
import com.cm.infrastructure.persistence.row.DepartmentDetailRow;
import com.cm.infrastructure.service.CmDepartmentService;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门下拉与车辆分组管理实现。校验口径对齐旧 DepartmentAction：
 * 上级不能是自己或自己的下级，有车或有子组不可删除；新增后自动授权给创建人。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentAppServiceImpl implements DepartmentAppService {

    private static final long ROOT_PARENT_ID = 0L;

    private final ReportQueryService reportQueryService;

    private final CmDepartmentService departmentService;

    private final DataScopeService dataScopeService;

    @Override
    public List<DepartmentVO> list(Long userId) {
        DataScope scope = dataScopeService.resolve(userId);
        return reportQueryService.departments(scope).stream()
                .map(ViewConverter::toDepartmentVO)
                .toList();
    }

    @Override
    public List<DepartmentDetailVO> listManage(Long userId, String keyword) {
        DataScope scope = dataScopeService.resolve(userId);
        return departmentService.listInScope(scope, keyword).stream()
                .map(DepartmentConverter::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentDetailVO create(Long userId, DepartmentSaveRequest request) {
        DataScope scope = dataScopeService.resolve(userId);
        String name = request.getName().trim();
        if (departmentService.existsName(name, null)) {
            throw new CmBusinessException("车组名称已存在（含已删除车组，名称全局唯一）");
        }
        long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        DepartmentDetailRow parent = parentId == ROOT_PARENT_ID ? null : requireDepartment(scope, parentId);
        CmDepartment entity = new CmDepartment();
        DepartmentConverter.apply(request, entity);
        entity.setCompanyId(resolveCompanyId(scope, parent));
        entity.setDeleted(false);
        try {
            departmentService.save(entity);
        } catch (DuplicateKeyException ex) {
            throw new CmBusinessException("车组名称已存在（含已删除车组，名称全局唯一）");
        }
        departmentService.grantToUser(userId, entity.getId());
        log.info("车辆分组已创建, userId={}, departmentId={}, parentId={}", userId, entity.getId(), parentId);
        // 授权后重新解析范围，创建人才能立即读到新分组。
        return DepartmentConverter.toVO(requireDepartment(dataScopeService.resolve(userId), entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentDetailVO update(Long userId, Long id, DepartmentSaveRequest request) {
        DataScope scope = dataScopeService.resolve(userId);
        DepartmentDetailRow existing = requireDepartment(scope, id);
        String name = request.getName().trim();
        if (departmentService.existsName(name, id)) {
            throw new CmBusinessException("车组名称已存在（含已删除车组，名称全局唯一）");
        }
        long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        DepartmentDetailRow parent = null;
        if (parentId != ROOT_PARENT_ID) {
            if (parentId == id) {
                throw new CmBusinessException("上级车组不能是本车组");
            }
            if (departmentService.descendantIds(id).contains(parentId)) {
                throw new CmBusinessException("上级车组不能同时是本车组的下级车组");
            }
            parent = requireDepartment(scope, parentId);
        }
        CmDepartment entity = new CmDepartment();
        DepartmentConverter.apply(request, entity);
        entity.setId(id);
        entity.setCompanyId(parent == null ? existing.getCompanyId() : parent.getCompanyId());
        try {
            departmentService.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new CmBusinessException("车组名称已存在（含已删除车组，名称全局唯一）");
        }
        log.info("车辆分组已修改, userId={}, departmentId={}, parentId={}", userId, id, parentId);
        return DepartmentConverter.toVO(requireDepartment(scope, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        DataScope scope = dataScopeService.resolve(userId);
        requireDepartment(scope, id);
        if (departmentService.countVehicles(id) > 0) {
            throw new CmConflictException("该车组下分配有车辆，不能删除");
        }
        if (departmentService.countChildren(id) > 0) {
            throw new CmConflictException("该车组下有子部门，不能删除");
        }
        departmentService.removeById(id);
        log.info("车辆分组已删除, userId={}, departmentId={}", userId, id);
    }

    /**
     * 范围外与不存在都拒绝，避免用 id 探测他人分组是否存在。
     */
    private DepartmentDetailRow requireDepartment(DataScope scope, long id) {
        DepartmentDetailRow row = departmentService.detailInScope(scope, id);
        if (row == null) {
            throw new AccessDeniedException("目标车组不在当前账号的数据范围内");
        }
        return row;
    }

    private long resolveCompanyId(DataScope scope, DepartmentDetailRow parent) {
        if (parent != null) {
            return parent.getCompanyId() == null ? 0L : parent.getCompanyId();
        }
        return scope.getCompanyIds().isEmpty() ? 0L : scope.getCompanyIds().get(0);
    }
}
