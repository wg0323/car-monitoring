package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.config.MonitorProperties;
import com.cm.admin.converter.ViewConverter;
import com.cm.admin.monitor.OnlineVehicleResolver;
import com.cm.admin.service.MonitorAppService;
import com.cm.admin.vo.MonitorSummaryVO;
import com.cm.admin.vo.MonitorVehicleVO;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.monitor.PositionSnapshot;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时监控实现。数据范围口径与车辆列表完全一致：范围只由登录主体生成，
 * 部门筛选值越界直接拒绝，不接受客户端自带的权限参数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorAppServiceImpl implements MonitorAppService {

    private static final long MILLIS_PER_SECOND = 1000L;

    private final CmVehicleService vehicleService;

    private final DataScopeService dataScopeService;

    private final MonitorPositionStore positionStore;

    private final MonitorProperties properties;

    private final OnlineVehicleResolver onlineVehicleResolver;

    /** 状态栏缓存。按账号维度，范围随账号变化，不需要再拼部门摘要。 */
    private final Map<Long, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    @Override
    public PageResult<MonitorVehicleVO> page(Long userId, long pageNo, long pageSize, String keyword,
                                            Long departmentId, Boolean online) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<VehicleRow> page = vehicleService.pageByScope(scope, pageNo, pageSize, keyword, null,
                departmentId, online, onlineVehicleResolver.resolve(scope, online));
        List<VehicleRow> rows = page.getRecords();
        List<String> simNos = rows.stream()
                .map(VehicleRow::getSimNo)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, PositionSnapshot> snapshots = positionStore.latestAll(simNos);
        Map<String, Boolean> onlineStates = positionStore.onlineAll(simNos);
        List<MonitorVehicleVO> records = rows.stream()
                .map(row -> ViewConverter.toMonitorVehicleVO(row,
                        row.getSimNo() == null ? null : snapshots.get(row.getSimNo()),
                        Boolean.TRUE.equals(onlineStates.get(row.getSimNo()))))
                .toList();
        log.debug("实时监控列表, userId={}, total={}, size={}", userId, page.getTotal(), records.size());
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public MonitorSummaryVO summary(Long userId) {
        DataScope scope = dataScopeService.resolve(userId);
        if (properties.getSummaryCacheSeconds() <= 0) {
            return computeSummary(scope);
        }
        MonitorSummaryVO cached = cachedSummary(userId);
        if (cached != null) {
            return cached;
        }
        MonitorSummaryVO computed = computeSummary(scope);
        summaryCache.put(userId, new CachedSummary(computed, System.currentTimeMillis()
                + properties.getSummaryCacheSeconds() * MILLIS_PER_SECOND));
        return computed;
    }

    @Override
    public Map<Long, String> vehiclesInScope(Long userId, Collection<Long> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return Map.of();
        }
        DataScope scope = dataScopeService.resolve(userId);
        List<Long> ids = vehicleIds.stream().filter(Objects::nonNull).distinct().toList();
        List<VehicleRow> rows = vehicleService.listInScopeByIds(scope, ids);
        if (rows.size() < ids.size()) {
            log.debug("订阅校验过滤越界车辆, userId={}, requested={}, allowed={}", userId, ids.size(), rows.size());
        }
        Map<Long, String> allowed = new LinkedHashMap<>(rows.size());
        for (VehicleRow row : rows) {
            if (row.getId() != null && row.getSimNo() != null) {
                allowed.put(row.getId(), row.getSimNo());
            }
        }
        return allowed;
    }

    private MonitorSummaryVO computeSummary(DataScope scope) {
        long total = vehicleService.countByScope(scope);
        List<String> onlineInScope = vehicleService.onlineSimNosInScope(scope, positionStore.onlineSimNos());
        long online = onlineInScope.size();
        long parking = online == 0 ? 0 : positionStore.parkingCount(onlineInScope);
        MonitorSummaryVO vo = new MonitorSummaryVO();
        vo.setTotal(total);
        vo.setOnline(online);
        vo.setOffline(Math.max(0, total - online));
        vo.setParking(parking);
        vo.setOnlineRate(rate(online, total));
        return vo;
    }

    private MonitorSummaryVO cachedSummary(Long userId) {
        CachedSummary cached = summaryCache.get(userId);
        if (cached == null || cached.expiresAtMillis() < System.currentTimeMillis()) {
            summaryCache.remove(userId);
            return null;
        }
        return cached.value();
    }

    private double rate(long online, long total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(online * 100.0 / total).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void assertDepartmentInScope(DataScope scope, Long departmentId) {
        if (!scope.allowsDepartment(departmentId)) {
            throw new AccessDeniedException("所选部门不在当前账号的数据范围内");
        }
    }

    private record CachedSummary(MonitorSummaryVO value, long expiresAtMillis) {
    }
}
