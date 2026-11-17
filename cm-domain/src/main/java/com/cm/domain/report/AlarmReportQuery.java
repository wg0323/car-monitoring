package com.cm.domain.report;

import java.time.LocalDateTime;

/**
 * 报警报表查询条件。alarmChannel 用于区分报警查询（全部通道）与停车报表（PLATFORM 通道）。
 */
public record AlarmReportQuery(String alarmChannel, String alarmType, String alarmSource, String plateNo,
                               Long departmentId, LocalDateTime startTime, LocalDateTime endTime) {
}
