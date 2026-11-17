package com.cm.domain.fence;

import com.cm.domain.exception.CmBusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 区域保存规则。旧系统 saveArea 的校验散在 Action 与前端 validate 中，
 * 这里收拢为一处纯函数：API 层与测试共用同一套判定，不依赖 Spring。
 */
public final class FencePolicy {

    public static final int MAX_NAME_LENGTH = 64;

    public static final int MAX_REMARK_LENGTH = 255;

    public static final int MAX_DELAY_SECONDS = 3600;

    public static final BigDecimal MAX_SPEED_KPH = new BigDecimal("200");

    private FencePolicy() {
    }

    /**
     * 校验整条保存命令并返回解析后的几何。调用方只需再按 geometry 落库，不再重复判定。
     */
    public static FenceGeometry validate(FenceCommand command) {
        if (command == null) {
            throw new CmBusinessException("区域信息不能为空");
        }
        requireName(command.name());
        requireDepartment(command.departmentId());
        FenceGeometry geometry = FenceGeometry.parse(command.areaType(), command.points(), command.radius());
        requireAlarmType(command);
        requireTimeWindow(command);
        requireLimitSpeed(command);
        requireDelay(command.delaySeconds());
        requireRemark(command.remark());
        return geometry;
    }

    public static String normalizeName(String name) {
        requireName(name);
        return name.trim();
    }

    public static String normalizeRemark(String remark) {
        return remark == null ? "" : remark.trim();
    }

    /** 旧系统要求「报警类型至少要选择一种」，四个复选框全空时拒绝保存。 */
    private static void requireAlarmType(FenceCommand command) {
        boolean anyAlarm = command.alarmEnterDriver()
                || command.alarmEnterPlatform()
                || command.alarmExitDriver()
                || command.alarmExitPlatform();
        if (!anyAlarm) {
            throw new CmBusinessException("报警类型至少要选择一种");
        }
    }

    private static void requireDepartment(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            throw new CmBusinessException("请选择所属部门");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new CmBusinessException("区域名称不能为空");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new CmBusinessException("区域名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
    }

    private static void requireTimeWindow(FenceCommand command) {
        if (!command.byTime()) {
            return;
        }
        LocalDateTime start = command.startDate();
        LocalDateTime end = command.endDate();
        if (start == null || end == null) {
            throw new CmBusinessException("启用时间段时必须填写开始与结束时间");
        }
        if (!end.isAfter(start)) {
            throw new CmBusinessException("结束时间必须晚于开始时间");
        }
    }

    private static void requireLimitSpeed(FenceCommand command) {
        if (!command.limitSpeed()) {
            return;
        }
        BigDecimal maxSpeed = command.maxSpeed();
        if (maxSpeed == null || maxSpeed.signum() <= 0) {
            throw new CmBusinessException("启用限速时必须填写大于 0 的限速值");
        }
        if (maxSpeed.compareTo(MAX_SPEED_KPH) > 0) {
            throw new CmBusinessException("区域限速不能超过 " + MAX_SPEED_KPH + " km/h");
        }
    }

    private static void requireDelay(Integer delaySeconds) {
        if (delaySeconds == null || delaySeconds < 0 || delaySeconds > MAX_DELAY_SECONDS) {
            throw new CmBusinessException("延时报警必须在 0~" + MAX_DELAY_SECONDS + " 秒之间");
        }
    }

    private static void requireRemark(String remark) {
        if (remark != null && remark.trim().length() > MAX_REMARK_LENGTH) {
            throw new CmBusinessException("备注不能超过 " + MAX_REMARK_LENGTH + " 个字符");
        }
    }
}
