package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.ViewConverter;
import com.cm.admin.monitor.OnlineVehicleResolver;
import com.cm.admin.service.VehicleAppService;
import com.cm.admin.vo.VehicleDetailVO;
import com.cm.admin.vo.VehicleVO;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.VehicleDetailRow;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 车辆列表与详情实现。数据范围只接受登录主体 ID，部门筛选值先校验再下推 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleAppServiceImpl implements VehicleAppService {

    private final CmVehicleService vehicleService;

    private final DataScopeService dataScopeService;

    private final OnlineVehicleResolver onlineVehicleResolver;

    @Override
    public PageResult<VehicleVO> page(Long userId, long pageNo, long pageSize, String plateNo, String simNo,
                                      Long departmentId, Boolean online) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<VehicleRow> page = vehicleService.pageByScope(scope, pageNo, pageSize, plateNo, simNo,
                departmentId, online, onlineVehicleResolver.resolve(scope, online));
        List<VehicleVO> records = page.getRecords().stream()
                .map(ViewConverter::toVehicleVO)
                .toList();
        log.debug("车辆列表, userId={}, total={}, size={}", userId, page.getTotal(), records.size());
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public VehicleDetailVO detail(Long userId, Long vehicleId) {
        DataScope scope = dataScopeService.resolve(userId);
        VehicleDetailRow row = vehicleService.detailInScope(scope, vehicleId);
        if (row == null) {
            // 与用户详情同口径：不区分「不存在」与「越界」，避免用 id 探测他人车辆。
            throw new AccessDeniedException("目标车辆不在当前账号的数据范围内");
        }
        log.debug("车辆详情, userId={}, vehicleId={}", userId, vehicleId);
        return ViewConverter.toVehicleDetailVO(row);
    }

    /**
     * 部门筛选值越界即拒绝。修复旧系统「客户端自带 depIdList 就能绕过过滤」的缺陷：
     * 这里不合并请求参数，只用请求值与服务端范围比对。
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
