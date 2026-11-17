package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import com.cm.infrastructure.persistence.row.VehicleDetailRow;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmVehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 车辆查询实现。复杂关联 SQL 由 VehicleMapper.xml 提供，不在 Service 里拼条件。
 */
@Slf4j
@Service
public class CmVehicleServiceImpl extends ServiceImpl<CmVehicleMapper, CmVehicle> implements CmVehicleService {

    @Override
    public IPage<VehicleRow> pageByScope(DataScope scope, long pageNo, long pageSize, String plateNo, String simNo) {
        return pageByScope(scope, pageNo, pageSize, plateNo, simNo, null);
    }

    @Override
    public IPage<VehicleRow> pageByScope(DataScope scope, long pageNo, long pageSize, String plateNo, String simNo,
                                        Long departmentId) {
        return pageByScope(scope, pageNo, pageSize, plateNo, simNo, departmentId, null, null);
    }

    @Override
    public IPage<VehicleRow> pageByScope(DataScope scope, long pageNo, long pageSize, String plateNo, String simNo,
                                        Long departmentId, Boolean online, Collection<String> onlineSimNos) {
        requireScope(scope);
        long offset = computeOffset(pageNo, pageSize);
        long total = baseMapper.countVehiclePage(scope, plateNo, simNo, departmentId, online, onlineSimNos);
        List<VehicleRow> rows = baseMapper.selectVehiclePage(scope, plateNo, simNo, departmentId, online,
                onlineSimNos, offset, pageSize);
        Page<VehicleRow> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public VehicleDetailRow detailInScope(DataScope scope, long id) {
        requireScope(scope);
        return baseMapper.selectVehicleDetail(scope, id);
    }

    @Override
    public List<String> onlineSimNosInScope(DataScope scope, Collection<String> onlineSimNos) {
        requireScope(scope);
        if (onlineSimNos == null || onlineSimNos.isEmpty()) {
            return List.of();
        }
        List<String> ordered = List.copyOf(onlineSimNos);
        List<String> inScope = new ArrayList<>(ordered.size());
        for (int start = 0; start < ordered.size(); start += DataScope.MAX_SCOPE_IDS) {
            List<String> chunk = ordered.subList(start, Math.min(start + DataScope.MAX_SCOPE_IDS, ordered.size()));
            for (VehicleRow row : baseMapper.selectVehiclesInScopeBySimNos(scope, chunk)) {
                if (row.getSimNo() != null) {
                    inScope.add(row.getSimNo());
                }
            }
        }
        return inScope;
    }

    @Override
    public List<VehicleRow> treeByScope(DataScope scope, String plateNo) {
        requireScope(scope);
        return baseMapper.selectVehicleTree(scope, plateNo);
    }

    @Override
    public long countByScope(DataScope scope) {
        requireScope(scope);
        return baseMapper.countVehiclePage(scope, null, null, null, null, null);
    }

    @Override
    public List<VehicleRow> listInScopeBySimNos(DataScope scope, Collection<String> simNos) {
        requireScope(scope);
        if (simNos.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectVehiclesInScopeBySimNos(scope, simNos);
    }

    @Override
    public List<VehicleRow> listInScopeByIds(DataScope scope, Collection<Long> ids) {
        requireScope(scope);
        if (ids.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectVehiclesInScopeByIds(scope, ids);
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
