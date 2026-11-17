package com.cm.domain.command;

import java.time.LocalDateTime;

/**
 * 终端命令结果页的查询条件。只承载业务筛选，
 * 数据范围与操作人由服务端另行注入，不从请求参数反序列化。
 * 使用标准 getter 而不是 record，保证 MyBatis OGNL 在 XML 里能稳定解析属性。
 */
public final class CommandQuery {

    private final String plateNo;

    private final String status;

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;

    public CommandQuery(String plateNo, String status, LocalDateTime startTime, LocalDateTime endTime) {
        this.plateNo = plateNo;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }
}
