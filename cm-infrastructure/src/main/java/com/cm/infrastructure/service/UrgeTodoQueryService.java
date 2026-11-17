package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 报警督办读模型。上级下发的督办由链路进程落库，管理端只读并写应答指令。
 */
public interface UrgeTodoQueryService {

    IPage<CmWarnMsgUrgeTodoReq> pageByQuery(String plateNo, Boolean acknowledged,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            long pageNo, long pageSize);

    /**
     * 增量督办：只返回未应答且 id 大于 sinceId 的记录，供页面自动弹窗。
     */
    List<CmWarnMsgUrgeTodoReq> listNewUrgeTodos(long sinceId, LocalDateTime startTime, int limit);

    CmWarnMsgUrgeTodoReq load(long id);
}
