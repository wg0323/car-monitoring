package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmAlarmRecord;
import com.cm.infrastructure.persistence.row.AlarmRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警记录与处理审计。处理动作必须在一个事务里完成状态更新和审计写入；
 * 开户 / 关户方法供定位链路的报警消费者调用。
 */
public interface CmAlarmRecordService extends IService<CmAlarmRecord> {

    IPage<AlarmRow> pageByScope(DataScope scope, String alarmType, Boolean processed, String plateNo,
                                LocalDateTime startTime, LocalDateTime endTime,
                                long pageNo, long pageSize);

    /**
     * 增量报警：按主键升序取 sinceId 之后、不早于 startTime 的记录，供弹窗轮询。
     */
    List<AlarmRow> listNewAlarms(DataScope scope, long sinceId, LocalDateTime startTime, int limit);

    void process(DataScope scope, Long alarmId, Long processorId, String processorName,
                 String result, String remark);

    /**
     * 开户。同一 event_key 已存在时返回 false，由 uk_event_key 唯一键保证只写一次。
     */
    boolean tryOpenAlarm(CmAlarmRecord record);

    /**
     * 关户，返回影响行数；为 0 表示已经没有未结束记录。
     */
    int closeOpenAlarm(String simNo, String alarmType, String alarmChannel, LocalDateTime endTime,
                       BigDecimal latitude, BigDecimal longitude, BigDecimal mileage, BigDecimal gas);

    /**
     * 该 SIM 在指定通道下当前未结束的报警类型，用于消费端重启后的状态机基线。
     */
    List<String> openAlarmTypes(String simNo, String alarmChannel);
}
