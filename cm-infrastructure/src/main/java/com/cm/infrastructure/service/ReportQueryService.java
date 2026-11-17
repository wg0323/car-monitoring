package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.domain.report.AlarmReportQuery;
import com.cm.domain.report.OnlineRecordQuery;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.persistence.row.AlarmStatisticRow;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.MileageStatisticRow;
import com.cm.infrastructure.persistence.row.OnlineRateRow;
import com.cm.infrastructure.persistence.row.OnlineRecordRow;
import com.cm.infrastructure.persistence.row.OperationLogRow;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表查询服务。每个方法是固定命名查询，替代旧系统前端直传 queryId 的机制。
 */
public interface ReportQueryService {

    List<DepartmentRow> departments(DataScope scope);

    List<OperationLogRow> operationLog(DataScope scope, String userName,
                                       LocalDateTime startTime, LocalDateTime endTime);

    List<OnlineRateRow> departmentOnlineRate(DataScope scope, LocalDateTime startTime, LocalDateTime endTime);

    List<OnlineRateRow> vehicleOnlineRate(DataScope scope, LocalDateTime startTime, LocalDateTime endTime);

    List<MileageStatisticRow> mileageStatistic(DataScope scope, LocalDateTime startTime, LocalDateTime endTime);

    List<OnlineRecordRow> onlineRecord(DataScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                       String plateNo);

    List<AlarmStatisticRow> alarmStatistic(DataScope scope, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 报警查询分页，列表页使用。
     */
    IPage<AlarmRow> alarmPage(DataScope scope, AlarmReportQuery query, long pageNo, long pageSize);

    /**
     * 报警查询导出，与分页同一筛选条件，maxRows 限制内存占用。
     */
    List<AlarmRow> alarmRows(DataScope scope, AlarmReportQuery query, long maxRows);

    IPage<OnlineRecordRow> onlineRecordPage(DataScope scope, OnlineRecordQuery query, long pageNo, long pageSize);

    List<OnlineRecordRow> onlineRecordRows(DataScope scope, OnlineRecordQuery query, long maxRows);

    /**
     * 当前在线报表的车辆候选，在线态与离线时长由上层用 Redis 补齐。
     */
    List<VehicleOnlineReportRow> onlineVehicleCandidates(DataScope scope, String plateNo, String simNo,
                                                         Long departmentId);
}
