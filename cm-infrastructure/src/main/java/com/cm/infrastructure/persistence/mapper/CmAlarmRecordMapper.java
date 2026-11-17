package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmAlarmRecord;
import com.cm.infrastructure.persistence.row.AlarmRow;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警表 Mapper。合并后的单表查询与条件状态更新写在 mapper/AlarmRecordMapper.xml。
 */
public interface CmAlarmRecordMapper extends BaseMapper<CmAlarmRecord> {

    List<AlarmRow> selectAlarmPage(@Param("scope") DataScope scope,
                                   @Param("alarmType") String alarmType,
                                   @Param("processed") Boolean processed,
                                   @Param("plateNo") String plateNo,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    long countAlarmPage(@Param("scope") DataScope scope,
                        @Param("alarmType") String alarmType,
                        @Param("processed") Boolean processed,
                        @Param("plateNo") String plateNo,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

    /**
     * 增量报警：按主键升序取 sinceId 之后的未删除记录，供页面轮询弹窗。
     */
    List<AlarmRow> selectNewAlarms(@Param("scope") DataScope scope,
                                   @Param("sinceId") long sinceId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("limit") int limit);

    int markProcessed(@Param("scope") DataScope scope,
                      @Param("alarmId") Long alarmId,
                      @Param("processorId") Long processorId,
                      @Param("processorName") String processorName,
                      @Param("remark") String remark,
                      @Param("processTime") LocalDateTime processTime);

    /**
     * 报表分页：与报警处理页分开，筛选条件含报警类型、来源、车牌与部门。
     */
    List<AlarmRow> selectAlarmReportPage(@Param("scope") DataScope scope,
                                         @Param("alarmChannel") String alarmChannel,
                                         @Param("alarmType") String alarmType,
                                         @Param("alarmSource") String alarmSource,
                                         @Param("plateNo") String plateNo,
                                         @Param("departmentId") Long departmentId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("offset") long offset,
                                         @Param("size") long size);

    long countAlarmReport(@Param("scope") DataScope scope,
                          @Param("alarmChannel") String alarmChannel,
                          @Param("alarmType") String alarmType,
                          @Param("alarmSource") String alarmSource,
                          @Param("plateNo") String plateNo,
                          @Param("departmentId") Long departmentId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 导出用全量行，与分页查询同一筛选条件，依靠 maxRows 限制内存占用。
     */
    List<AlarmRow> selectAlarmRows(@Param("scope") DataScope scope,
                                   @Param("alarmChannel") String alarmChannel,
                                   @Param("alarmType") String alarmType,
                                   @Param("alarmSource") String alarmSource,
                                   @Param("plateNo") String plateNo,
                                   @Param("departmentId") Long departmentId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("maxRows") long maxRows);

    /**
     * 该 SIM 在指定通道下当前未结束的报警类型，消费端重启后用它重建状态机基线。
     */
    List<String> selectOpenAlarmTypes(@Param("simNo") String simNo,
                                      @Param("alarmChannel") String alarmChannel);

    /**
     * 关户：影响行数为 0 表示早已关闭或被其他实例关掉，调用方按重复事件处理。
     */
    int closeOpenAlarm(@Param("simNo") String simNo,
                       @Param("alarmType") String alarmType,
                       @Param("alarmChannel") String alarmChannel,
                       @Param("endTime") LocalDateTime endTime,
                       @Param("latitude") BigDecimal latitude,
                       @Param("longitude") BigDecimal longitude,
                       @Param("mileage") BigDecimal mileage,
                       @Param("gas") BigDecimal gas);
}
