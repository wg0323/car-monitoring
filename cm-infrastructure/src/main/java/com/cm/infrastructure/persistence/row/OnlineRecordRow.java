package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上下线记录结果行，对应旧 report.selectOnlineRecord。
 */
@Data
public class OnlineRecordRow {

    private String plateNo;

    private Integer plateColor;

    private String departmentName;

    private String eventType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    private BigDecimal timeSpan;

    private BigDecimal velocity;

    private String driver;

    private String alarmType;
}
