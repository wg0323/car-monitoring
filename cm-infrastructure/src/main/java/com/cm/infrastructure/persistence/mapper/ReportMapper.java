package com.cm.infrastructure.persistence.mapper;

import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.AlarmStatisticRow;
import com.cm.infrastructure.persistence.row.DepartmentRow;
import com.cm.infrastructure.persistence.row.MileageStatisticRow;
import com.cm.infrastructure.persistence.row.OnlineRateRow;
import com.cm.infrastructure.persistence.row.OnlineRecordRow;
import com.cm.infrastructure.persistence.row.OperationLogRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表查询 Mapper。全部为显式 XML 命名查询，不接收前端传入的 MyBatis statement ID。
 */
public interface ReportMapper {

    List<DepartmentRow> selectDepartments(@Param("scope") DataScope scope);

    List<OperationLogRow> selectOperationLog(@Param("scope") DataScope scope,
                                             @Param("userName") String userName,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    List<OnlineRateRow> selectDepartmentOnlineRate(@Param("scope") DataScope scope,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    List<OnlineRateRow> selectVehicleOnlineRate(@Param("scope") DataScope scope,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    List<MileageStatisticRow> selectMileageStatistic(@Param("scope") DataScope scope,
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    List<OnlineRecordRow> selectOnlineRecord(@Param("scope") DataScope scope,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              @Param("plateNo") String plateNo);

    /**
     * 上下线记录报表分页。筛选条件与导出共用，字段口径对齐旧 report.selectOnlineRecord。
     */
    List<OnlineRecordRow> selectOnlineRecordPage(@Param("scope") DataScope scope,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime,
                                                 @Param("plateNo") String plateNo,
                                                 @Param("eventType") String eventType,
                                                 @Param("departmentId") Long departmentId,
                                                 @Param("offset") long offset,
                                                 @Param("size") long size);

    long countOnlineRecord(@Param("scope") DataScope scope,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime,
                           @Param("plateNo") String plateNo,
                           @Param("eventType") String eventType,
                           @Param("departmentId") Long departmentId);

    List<OnlineRecordRow> selectOnlineRecordRows(@Param("scope") DataScope scope,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime,
                                                 @Param("plateNo") String plateNo,
                                                 @Param("eventType") String eventType,
                                                 @Param("departmentId") Long departmentId,
                                                 @Param("maxRows") long maxRows);

    List<AlarmStatisticRow> selectAlarmStatistic(@Param("scope") DataScope scope,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);
}
