package com.cm.admin.converter;

import com.cm.admin.vo.FenceSaveRequest;
import com.cm.admin.vo.FenceVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.fence.FenceCommand;
import com.cm.domain.fence.FenceGeometry;
import com.cm.domain.fence.FencePolicy;
import com.cm.infrastructure.persistence.entity.CmMapArea;
import com.cm.infrastructure.persistence.row.MapAreaRow;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 区域请求、持久化对象与响应之间的转换。
 * 同名同类型字段交给 BeanUtils，类型或名称不一致的字段在这里显式处理。
 */
public final class FenceConverter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String DATE_TIME_PATTERN_HINT = "yyyy-MM-dd HH:mm:ss";

    private static final int COORD_SCALE = 6;

    /** 数据范围只按部门生效，企业列与现有业务表同口径落 0。 */
    private static final long DEFAULT_COMPANY_ID = 0L;

    private FenceConverter() {
    }

    public static FenceCommand toCommand(FenceSaveRequest request) {
        return new FenceCommand(
                request.getName(),
                request.getDepartmentId(),
                request.getAreaType(),
                request.getPoints(),
                request.getRadius(),
                Boolean.TRUE.equals(request.getAlarmEnterDriver()),
                Boolean.TRUE.equals(request.getAlarmEnterPlatform()),
                Boolean.TRUE.equals(request.getAlarmExitDriver()),
                Boolean.TRUE.equals(request.getAlarmExitPlatform()),
                Boolean.TRUE.equals(request.getByTime()),
                parseTime(request.getStartDate(), "开始时间"),
                parseTime(request.getEndDate(), "结束时间"),
                Boolean.TRUE.equals(request.getLimitSpeed()),
                request.getMaxSpeed(),
                request.getDelaySeconds() == null ? 0 : request.getDelaySeconds(),
                request.getRemark());
    }

    /**
     * 按校验后的几何生成待保存实体；时间窗与限速未启用时显式落空值/0，避免开关关了还留着旧参数。
     */
    public static CmMapArea toEntity(FenceCommand command, FenceGeometry geometry) {
        CmMapArea entity = new CmMapArea();
        entity.setCompanyId(DEFAULT_COMPANY_ID);
        entity.setDepartmentId(command.departmentId());
        entity.setName(FencePolicy.normalizeName(command.name()));
        entity.setAreaType(geometry.getAreaType().getCode());
        entity.setPoints(geometry.normalizedPoints());
        entity.setCenterLat(toCoordinate(geometry.getCenter().lat()));
        entity.setCenterLng(toCoordinate(geometry.getCenter().lng()));
        entity.setRadius(geometry.getRadiusMeters());
        entity.setAlarmEnterDriver(command.alarmEnterDriver());
        entity.setAlarmEnterPlatform(command.alarmEnterPlatform());
        entity.setAlarmExitDriver(command.alarmExitDriver());
        entity.setAlarmExitPlatform(command.alarmExitPlatform());
        entity.setByTime(command.byTime());
        entity.setStartDate(command.byTime() ? command.startDate() : null);
        entity.setEndDate(command.byTime() ? command.endDate() : null);
        entity.setLimitSpeed(command.limitSpeed());
        entity.setMaxSpeed(command.limitSpeed() ? command.maxSpeed() : BigDecimal.ZERO);
        entity.setDelaySeconds(command.delaySeconds());
        entity.setRemark(FencePolicy.normalizeRemark(command.remark()));
        return entity;
    }

    public static FenceVO toVO(MapAreaRow row) {
        FenceVO vo = new FenceVO();
        BeanUtils.copyProperties(row, vo);
        vo.setCenterLat(toDouble(row.getCenterLat()));
        vo.setCenterLng(toDouble(row.getCenterLng()));
        vo.setAlarmEnterDriver(Boolean.TRUE.equals(row.getAlarmEnterDriver()));
        vo.setAlarmEnterPlatform(Boolean.TRUE.equals(row.getAlarmEnterPlatform()));
        vo.setAlarmExitDriver(Boolean.TRUE.equals(row.getAlarmExitDriver()));
        vo.setAlarmExitPlatform(Boolean.TRUE.equals(row.getAlarmExitPlatform()));
        vo.setByTime(Boolean.TRUE.equals(row.getByTime()));
        vo.setLimitSpeed(Boolean.TRUE.equals(row.getLimitSpeed()));
        vo.setStartTimeText(formatTime(row.getStartDate()));
        vo.setEndTimeText(formatTime(row.getEndDate()));
        vo.setCreateTimeText(formatTime(row.getCreateTime()));
        vo.setUpdateTimeText(formatTime(row.getUpdateTime()));
        return vo;
    }

    private static LocalDateTime parseTime(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (RuntimeException ex) {
            throw new CmBusinessException(fieldLabel + "格式不合法，应为 " + DATE_TIME_PATTERN_HINT);
        }
    }

    private static String formatTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private static BigDecimal toCoordinate(double value) {
        return BigDecimal.valueOf(value).setScale(COORD_SCALE, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
