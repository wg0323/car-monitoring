package com.cm.infrastructure.gov809;

import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;

/**
 * 809 报警督办。上级重发同一督办时只落一行（platform_id + supervise_id 唯一），
 * 应答用条件更新，重复应答不会把时间改来改去。
 */
public interface WarnMsgUrgeTodoService {

    /** 返回 false 表示该督办已存在。 */
    boolean record(CmWarnMsgUrgeTodoReq row);

    boolean acknowledge(long platformId, long superviseId, int result);

    CmWarnMsgUrgeTodoReq find(long platformId, long superviseId);
}
 
