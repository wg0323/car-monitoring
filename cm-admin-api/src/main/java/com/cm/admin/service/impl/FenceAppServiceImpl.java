package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.FenceConverter;
import com.cm.admin.service.FenceAppService;
import com.cm.admin.vo.FenceVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.fence.FenceCommand;
import com.cm.domain.fence.FenceGeometry;
import com.cm.domain.fence.FencePolicy;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmMapArea;
import com.cm.infrastructure.persistence.row.MapAreaRow;
import com.cm.infrastructure.service.CmMapAreaService;
import com.cm.infrastructure.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 区域管理实现。数据范围只接受登录主体 ID；保存前先校验目标部门在范围内，
 * 与车辆列表同一口径，避免「客户端自带部门就能改别人区域」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FenceAppServiceImpl implements FenceAppService {

    private final CmMapAreaService mapAreaService;

    private final DataScopeService dataScopeService;

    @Override
    public PageResult<FenceVO> page(Long userId, long pageNo, long pageSize, String name, Long departmentId) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<MapAreaRow> page = mapAreaService.pageByScope(scope, pageNo, pageSize, name, departmentId);
        List<FenceVO> records = page.getRecords().stream()
                .map(FenceConverter::toVO)
                .toList();
        log.debug("区域列表, userId={}, total={}, size={}", userId, page.getTotal(), records.size());
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public FenceVO get(Long userId, long id) {
        DataScope scope = dataScopeService.resolve(userId);
        return FenceConverter.toVO(requireArea(scope, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FenceVO create(Long userId, FenceCommand command) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, command.departmentId());
        FenceGeometry geometry = FencePolicy.validate(command);
        CmMapArea entity = FenceConverter.toEntity(command, geometry);
        mapAreaService.save(entity);
        log.info("区域已创建, userId={}, areaId={}, type={}, departmentId={}",
                userId, entity.getId(), entity.getAreaType(), entity.getDepartmentId());
        return FenceConverter.toVO(requireArea(scope, entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FenceVO update(Long userId, long id, FenceCommand command) {
        DataScope scope = dataScopeService.resolve(userId);
        requireArea(scope, id);
        assertDepartmentInScope(scope, command.departmentId());
        FenceGeometry geometry = FencePolicy.validate(command);
        CmMapArea entity = FenceConverter.toEntity(command, geometry);
        entity.setId(id);
        mapAreaService.updateById(entity);
        log.info("区域已修改, userId={}, areaId={}, type={}, departmentId={}",
                userId, id, entity.getAreaType(), entity.getDepartmentId());
        return FenceConverter.toVO(requireArea(scope, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, long id) {
        DataScope scope = dataScopeService.resolve(userId);
        requireArea(scope, id);
        mapAreaService.removeById(id);
        log.info("区域已删除, userId={}, areaId={}", userId, id);
    }

    private MapAreaRow requireArea(DataScope scope, long id) {
        MapAreaRow row = mapAreaService.getInScope(scope, id);
        if (row == null) {
            throw new CmBusinessException("区域不存在");
        }
        return row;
    }

    /**
     * 部门筛选值越界即拒绝。超管不限；请求值只与范围比对，不合并进权限参数。
     */
    private void assertDepartmentInScope(DataScope scope, Long departmentId) {
        if (!scope.allowsDepartment(departmentId)) {
            throw new AccessDeniedException("所选部门不在当前账号的数据范围内");
        }
    }
}
