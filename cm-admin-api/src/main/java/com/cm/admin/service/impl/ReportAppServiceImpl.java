package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.ReportConverter;
import com.cm.admin.service.ReportAppService;
import com.cm.admin.support.ReportExcelWriter;
import com.cm.admin.vo.ReportAlarmVO;
import com.cm.admin.vo.ReportOnlineRecordVO;
import com.cm.admin.vo.ReportOnlineVehicleVO;
import com.cm.admin.vo.ReportOptionsVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.report.AlarmReportQuery;
import com.cm.domain.report.OnlineRecordQuery;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.monitor.PositionSnapshot;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.persistence.row.OnlineRecordRow;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.service.ReportQueryService;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表查询与导出实现。列表与导出都走同一份查询条件，导出上限 5 万行防止同步下载把堆打满。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAppServiceImpl implements ReportAppService {

    private static final String CHANNEL_PLATFORM = "PLATFORM";

    private static final String TYPE_PARKING = "Parking";

    private static final String STATUS_ONLINE = "online";

    private static final String STATUS_OFFLINE = "offline";

    private static final int MINUTES_PER_DAY = 1440;

    private final ReportQueryService reportQueryService;

    private final DataScopeService dataScopeService;

    private final MonitorPositionStore monitorPositionStore;

    private final TelemetryProperties telemetryProperties;

    @Override
    public PageResult<ReportAlarmVO> alarmPage(Long userId, long page, long size, String plateNo,
                                               Long departmentId, String alarmType, String alarmSource,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<AlarmRow> result = reportQueryService.alarmPage(scope,
                new AlarmReportQuery(null, alarmType, alarmSource, plateNo, departmentId, startTime, endTime),
                page, size);
        List<ReportAlarmVO> records = result.getRecords().stream().map(ReportConverter::toAlarmVO).toList();
        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public void exportAlarms(Long userId, String plateNo, Long departmentId, String alarmType, String alarmSource,
                             LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        List<AlarmRow> rows = reportQueryService.alarmRows(scope,
                new AlarmReportQuery(null, alarmType, alarmSource, plateNo, departmentId, startTime, endTime),
                QueryLimits.MAX_EXPORT_ROWS);
        List<ReportAlarmVO> records = rows.stream().map(ReportConverter::toAlarmVO).toList();
        log.info("报警查询导出, userId={}, rows={}", userId, records.size());
        ReportExcelWriter.write(outputStream, "报警查询", ReportAlarmVO.class, records);
    }

    @Override
    public PageResult<ReportOnlineRecordVO> onlineRecordPage(Long userId, long page, long size, String plateNo,
                                                             Long departmentId, String eventType,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<OnlineRecordRow> result = reportQueryService.onlineRecordPage(scope,
                new OnlineRecordQuery(eventType, plateNo, departmentId, startTime, endTime), page, size);
        List<ReportOnlineRecordVO> records = result.getRecords().stream()
                .map(ReportConverter::toOnlineRecordVO).toList();
        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public void exportOnlineRecords(Long userId, String plateNo, Long departmentId, String eventType,
                                    LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        List<OnlineRecordRow> rows = reportQueryService.onlineRecordRows(scope,
                new OnlineRecordQuery(eventType, plateNo, departmentId, startTime, endTime),
                QueryLimits.MAX_EXPORT_ROWS);
        List<ReportOnlineRecordVO> records = rows.stream().map(ReportConverter::toOnlineRecordVO).toList();
        log.info("上下线记录导出, userId={}, rows={}", userId, records.size());
        ReportExcelWriter.write(outputStream, "车辆上下线记录", ReportOnlineRecordVO.class, records);
    }

    @Override
    public PageResult<ReportOnlineVehicleVO> onlineVehiclePage(Long userId, long page, long size, String plateNo,
                                                               String simNo, Long departmentId, String status,
                                                               Integer offlineDays) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        List<ReportOnlineVehicleVO> rows = loadOnlineVehicles(scope, plateNo, simNo, departmentId, status, offlineDays);
        long offset = computeOffset(page, size);
        long total = rows.size();
        int from = (int) Math.min(offset, total);
        int to = (int) Math.min(offset + size, total);
        return new PageResult<>(total, page, size, new ArrayList<>(rows.subList(from, to)));
    }

    @Override
    public void exportOnlineVehicles(Long userId, String plateNo, String simNo, Long departmentId, String status,
                                     Integer offlineDays, OutputStream outputStream) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        List<ReportOnlineVehicleVO> rows = loadOnlineVehicles(scope, plateNo, simNo, departmentId, status, offlineDays);
        if (rows.size() > QueryLimits.MAX_EXPORT_ROWS) {
            throw new CmBusinessException("导出行数超过 " + QueryLimits.MAX_EXPORT_ROWS + "，请缩小查询范围");
        }
        log.info("当前在线报表导出, userId={}, rows={}", userId, rows.size());
        ReportExcelWriter.write(outputStream, "当前车辆在线情况", ReportOnlineVehicleVO.class, rows);
    }

    @Override
    public PageResult<ReportAlarmVO> parkingPage(Long userId, long page, long size, String plateNo,
                                                 Long departmentId, String alarmSource,
                                                 LocalDateTime startTime, LocalDateTime endTime) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        IPage<AlarmRow> result = reportQueryService.alarmPage(scope,
                new AlarmReportQuery(CHANNEL_PLATFORM, TYPE_PARKING, alarmSource, plateNo, departmentId,
                        startTime, endTime), page, size);
        List<ReportAlarmVO> records = result.getRecords().stream().map(ReportConverter::toAlarmVO).toList();
        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public void exportParkings(Long userId, String plateNo, Long departmentId, String alarmSource,
                               LocalDateTime startTime, LocalDateTime endTime, OutputStream outputStream) {
        DataScope scope = dataScopeService.resolve(userId);
        assertDepartmentInScope(scope, departmentId);
        List<AlarmRow> rows = reportQueryService.alarmRows(scope,
                new AlarmReportQuery(CHANNEL_PLATFORM, TYPE_PARKING, alarmSource, plateNo, departmentId,
                        startTime, endTime), QueryLimits.MAX_EXPORT_ROWS);
        List<ReportAlarmVO> records = rows.stream().map(ReportConverter::toAlarmVO).toList();
        log.info("停车报警导出, userId={}, rows={}", userId, records.size());
        ReportExcelWriter.write(outputStream, "停车报警记录", ReportAlarmVO.class, records);
    }

    @Override
    public ReportOptionsVO options() {
        return ReportConverter.options();
    }

    /**
     * 当前在线跨两个存储：MySQL 出候选车辆，Redis 出在线租约与最新位置。
     * 状态与时长的过滤必须在合并后做，否则 total 与页面行数会对不上。
     */
    private List<ReportOnlineVehicleVO> loadOnlineVehicles(DataScope scope, String plateNo, String simNo,
                                                           Long departmentId, String status, Integer offlineDays) {
        List<VehicleOnlineReportRow> candidates = reportQueryService.onlineVehicleCandidates(scope, plateNo,
                simNo, departmentId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> simNos = candidates.stream()
                .map(VehicleOnlineReportRow::getSimNo)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        Map<String, Boolean> onlineStates = monitorPositionStore.onlineAll(simNos);
        Map<String, PositionSnapshot> snapshots = monitorPositionStore.latestAll(simNos);
        ZoneId zone = ZoneId.of(telemetryProperties.getTime().getZone());
        LocalDateTime now = LocalDateTime.now(zone);
        List<ReportOnlineVehicleVO> rows = new ArrayList<>();
        for (VehicleOnlineReportRow candidate : candidates) {
            boolean online = Boolean.TRUE.equals(onlineStates.get(candidate.getSimNo()));
            if (!matchOnlineStatus(online, status)) {
                continue;
            }
            ReportOnlineVehicleVO vo = ReportConverter.toOnlineVehicleVO(candidate, online,
                    snapshots.get(candidate.getSimNo()), zone, now);
            if (!matchOfflineDays(vo, offlineDays)) {
                continue;
            }
            rows.add(vo);
        }
        if (STATUS_OFFLINE.equals(status)) {
            rows.sort(Comparator.comparing(ReportOnlineVehicleVO::getOfflineMinutes,
                    Comparator.nullsLast(Comparator.<Long>reverseOrder())));
        }
        return rows;
    }

    private boolean matchOnlineStatus(boolean online, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        if (STATUS_ONLINE.equals(status)) {
            return online;
        }
        if (STATUS_OFFLINE.equals(status)) {
            return !online;
        }
        throw new CmBusinessException("在线状态取值不合法");
    }

    private boolean matchOfflineDays(ReportOnlineVehicleVO vo, Integer offlineDays) {
        if (offlineDays == null || offlineDays <= 0) {
            return true;
        }
        return vo.getOfflineMinutes() != null && vo.getOfflineMinutes() >= (long) offlineDays * MINUTES_PER_DAY;
    }

    private long computeOffset(long pageNo, long pageSize) {
        if (pageNo < 1 || pageNo > QueryLimits.MAX_PAGE_NO
                || pageSize < 1 || pageSize > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("分页参数超出允许范围");
        }
        return (pageNo - 1) * pageSize;
    }

    /**
     * 部门筛选值越界即拒绝，规则与车辆列表一致，避免报表成为绕过数据范围的入口。
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
