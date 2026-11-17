package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.UrgeTodoVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 报警督办。查看与应答都属平台级审计数据，不按车辆部门过滤。
 */
public interface UrgeTodoAppService {

    PageResult<UrgeTodoVO> page(long pageNo, long pageSize, String plateNo, Boolean acknowledged,
                                LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 增量督办：sinceId 之后、近 minutes 分钟内收到的未应答记录。
     */
    List<UrgeTodoVO> notifications(long sinceId, int minutes);

    /**
     * 受理应答：只写「待下发」指令，由企业侧链路进程发送 0x1401 并回写应答状态。
     */
    Long acknowledge(Long userId, Long urgeTodoId, int result);
}
