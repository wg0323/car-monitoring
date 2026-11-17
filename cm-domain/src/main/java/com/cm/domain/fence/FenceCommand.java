package com.cm.domain.fence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保存区域的领域入参。字段与旧系统 maparea 表单一一对应：
 * 四项报警对应旧 alarmType 的四个复选框，时间窗、限速与延时按 2026-11-17 裁决完整保留。
 */
public record FenceCommand(
        String name,
        Long departmentId,
        String areaType,
        String points,
        Integer radius,
        boolean alarmEnterDriver,
        boolean alarmEnterPlatform,
        boolean alarmExitDriver,
        boolean alarmExitPlatform,
        boolean byTime,
        LocalDateTime startDate,
        LocalDateTime endDate,
        boolean limitSpeed,
        BigDecimal maxSpeed,
        Integer delaySeconds,
        String remark) {
}
