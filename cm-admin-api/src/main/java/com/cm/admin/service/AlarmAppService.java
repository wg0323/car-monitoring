package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.AlarmNotificationVO;
import com.cm.admin.vo.AlarmProcessResultVO;
import com.cm.admin.vo.AlarmRecordVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警处理。底部「报警数据」与报警处理页走分页查询，右下角弹窗走按主键的增量轮询；
 * 处理动作只标记已处理并留备注与审计，不向终端与上级平台下发任何报文。
 */
public interface AlarmAppService {

    PageResult<AlarmRecordVO> page(Long userId, long pageNo, long pageSize, Boolean processed,
                                   String plateNo, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 增量报警：sinceId 之后、近 minutes 分钟内开始的记录。首次进入页面时由前端先取基线 id。
     */
    List<AlarmNotificationVO> notifications(Long userId, long sinceId, int minutes);

    /**
     * 单条处理：状态、处理人、处理时间与备注在同一条 UPDATE 里写入，
     * 再追加一行 cm_alarm_process 审计。
     */
    void process(Long userId, String userName, Long alarmId, String remark);

    /**
     * 批量处理：逐条独立事务，失败项按 ID 回报原因，不因一条越权或已处理而整体回滚。
     */
    AlarmProcessResultVO processBatch(Long userId, String userName, List<Long> alarmIds, String remark);
}
