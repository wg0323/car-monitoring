package com.cm.infrastructure.gov809;

import com.cm.infrastructure.persistence.entity.CmJt809Command;

/**
 * 809 指令与报文日志。旧系统靠这张表轮询下发指令，新系统只当审计与页面数据，
 * 写日志失败不得影响链路收发，所以实现侧吞掉异常并告警。
 */
public interface Jt809CommandLogService {

    void log(CmJt809Command row);
}
 
