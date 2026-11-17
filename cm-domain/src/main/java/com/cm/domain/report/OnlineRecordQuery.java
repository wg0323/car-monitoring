package com.cm.domain.report;

import java.time.LocalDateTime;

/**
 * 上下线记录报表查询条件。eventType 取 GpsOnline / GpsOffline。
 */
public record OnlineRecordQuery(String eventType, String plateNo, Long departmentId,
                                LocalDateTime startTime, LocalDateTime endTime) {
}
