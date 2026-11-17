package com.cm.domain.command;

import java.time.LocalDateTime;

/**
 * 809 指令与报文日志的查询条件。source 区分本端下发（SEND）与上级下发（RECEIVE），
 * 为空表示不过滤；时间窗按创建时间过滤。
 */
public final class GovCommandQuery {

    private final String source;

    private final String plateNo;

    private final String status;

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;

    public GovCommandQuery(String source, String plateNo, String status,
                           LocalDateTime startTime, LocalDateTime endTime) {
        this.source = source;
        this.plateNo = plateNo;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getSource() {
        return source;
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
