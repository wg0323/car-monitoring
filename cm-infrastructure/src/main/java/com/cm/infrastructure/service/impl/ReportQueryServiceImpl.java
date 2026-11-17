package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.report.AlarmReportQuery;
import com.cm.domain.report.OnlineRecordQuery;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.mapper.CmAlarmRecordMapper;
import com.cm.infrastructure.persistence.mapper.ReportMapper;
import com.cm.infrastructure.persistence.mapper.VehicleOnlineReportMapper;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.persistence.row.AlarmStatisticRow;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.MileageStatisticRow;
import com.cm.infrastructure.persistence.row.OnlineRateRow;
import com.cm.infrastructure.persistence.row.OnlineRecordRow;
import com.cm.infrastructure.persistence.row.OperationLogRow;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;
import com.cm.infrastructure.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表查询实现。列表与导出共用这些方法，避免旧系统两套参数构造导致导出只有列头。
 * 时间范围必填，防止无界查询把大结果集全量加载进 JVM。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    private final ReportMapper reportMapper;

    private final CmAlarmRecordMapper alarmRecordMapper;

    private final VehicleOnlineReportMapper vehicleOnlineReportMapper;

    @Override
    public List<DepartmentRow> departments(DataScope scope) {
        requireScope(scope);
        return reportMapper.selectDepartments(scope);
    }

    @Override
    public List<OperationLogRow> operationLog(DataScope scope, String userName,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        requireScope(scope);
        validateRange(startTime, endTime);
        return reportMapper.selectOperationLog(scope, userName, startTime, endTime);
    }

    @Override
    public List<OnlineRateRow> departmentOnlineRate(DataScope scope,
                                                     LocalDateTime startTime, LocalDateTime endTime) {
        requireScope(scope);
        validateRange(startTime, endTime);
        return reportMapper.selectDepartmentOnlineRate(scope, startTime, endTime);
    }

    @Override
    public List<OnlineRateRow> vehicleOnlineRate(DataScope scope,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        requireScope(scope);
        validateRange(startTime, endTime);
        return reportMapper.selectVehicleOnlineRate(scope, startTime, endTime);
    }

    @Override
    public List<MileageStatisticRow> mileageStatistic(DataScope scope,
                                                      LocalDateTime startTime, LocalDateTime endTime) {
        requireScope(scope);
        validateRange(startTime, endTime);
        return reportMapper.selectMileageStatistic(scope, startTime, endTime);
    }

    @Override
    public List<OnlineRecordRow> onlineRecord(DataScope scope, LocalDateTime startTime,
                                              LocalDateTime endTime, String plateNo) {
        requireScope(scope);
        validateRange(startTime, endTime);
        return reportMapper.selectOnlineRecord(scope, startTime, endTime, plateNo);
    }

    @Override
    public List<AlarmStatisticRow> alarmStatistic(DataScope scope,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        requireScope(scope);
        validateRange(startTime, endTime);
        return reportMapper.selectAlarmStatistic(scope, startTime, endTime);
    }

    @Override
    public IPage<AlarmRow> alarmPage(DataScope scope, AlarmReportQuery query, long pageNo, long pageSize) {
        requireScope(scope);
        validateRange(query.startTime(), query.endTime());
        long offset = computeOffset(pageNo, pageSize);
        long total = alarmRecordMapper.countAlarmReport(scope, query.alarmChannel(), query.alarmType(),
                query.alarmSource(), query.plateNo(), query.departmentId(), query.startTime(), query.endTime());
        List<AlarmRow> rows = alarmRecordMapper.selectAlarmReportPage(scope, query.alarmChannel(),
                query.alarmType(), query.alarmSource(), query.plateNo(), query.departmentId(),
                query.startTime(), query.endTime(), offset, pageSize);
        Page<AlarmRow> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public List<AlarmRow> alarmRows(DataScope scope, AlarmReportQuery query, long maxRows) {
        requireScope(scope);
        validateRange(query.startTime(), query.endTime());
        requireMaxRows(maxRows);
        return alarmRecordMapper.selectAlarmRows(scope, query.alarmChannel(), query.alarmType(),
                query.alarmSource(), query.plateNo(), query.departmentId(), query.startTime(), query.endTime(),
                maxRows);
    }

    @Override
    public IPage<OnlineRecordRow> onlineRecordPage(DataScope scope, OnlineRecordQuery query,
                                                    long pageNo, long pageSize) {
        requireScope(scope);
        validateRange(query.startTime(), query.endTime());
        long offset = computeOffset(pageNo, pageSize);
        long total = reportMapper.countOnlineRecord(scope, query.startTime(), query.endTime(),
                query.plateNo(), query.eventType(), query.departmentId());
        List<OnlineRecordRow> rows = reportMapper.selectOnlineRecordPage(scope, query.startTime(),
                query.endTime(), query.plateNo(), query.eventType(), query.departmentId(), offset, pageSize);
        Page<OnlineRecordRow> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public List<OnlineRecordRow> onlineRecordRows(DataScope scope, OnlineRecordQuery query, long maxRows) {
        requireScope(scope);
        validateRange(query.startTime(), query.endTime());
        requireMaxRows(maxRows);
        return reportMapper.selectOnlineRecordRows(scope, query.startTime(), query.endTime(),
                query.plateNo(), query.eventType(), query.departmentId(), maxRows);
    }

    @Override
    public List<VehicleOnlineReportRow> onlineVehicleCandidates(DataScope scope, String plateNo, String simNo,
                                                                 Long departmentId) {
        requireScope(scope);
        return vehicleOnlineReportMapper.selectOnlineVehicleCandidates(scope, plateNo, simNo, departmentId);
    }

    private void requireMaxRows(long maxRows) {
        if (maxRows < 1 || maxRows > QueryLimits.MAX_EXPORT_ROWS) {
            throw new CmBusinessException("导出行数上限必须在 1 到 " + QueryLimits.MAX_EXPORT_ROWS + " 之间");
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

    private void requireScope(DataScope scope) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
    }

    private void validateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            log.warn("报表查询缺少时间范围，已拒绝无界查询");
            throw new CmBusinessException("报表查询必须指定开始时间与结束时间");
        }
        if (endTime.isBefore(startTime)) {
            throw new CmBusinessException("结束时间不能早于开始时间");
        }
        if (Duration.between(startTime, endTime).toDays() > QueryLimits.MAX_RANGE_DAYS) {
            throw new CmBusinessException("报表查询时间跨度不能超过 " + QueryLimits.MAX_RANGE_DAYS + " 天");
        }
    }
}
