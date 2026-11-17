package com.cm.admin.converter;

import com.cm.admin.vo.TrackPointVO;
import com.cm.admin.vo.TrackResultVO;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.telemetry.TrackPoint;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 轨迹点与查询结果的展示转换。经纬度、速度、里程与油量按 808 原始单位换算，
 * 状态与方向沿用旧系统 getStatusDescr / getDirectionDescr 的口径。
 */
public final class TrackConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 808 经纬度为百万分之一度。 */
    private static final int DEGREE_SCALE = 6;

    private static final int ONE_DECIMAL_SCALE = 1;

    private static final int BIT_ACC = 1;

    private static final int BIT_LOCATED = 1 << 1;

    private static final int BIT_STOPPED = 1 << 4;

    private static final int DEGREE_FULL_CIRCLE = 360;

    private TrackConverter() {
    }

    public static TrackPointVO toPointVO(TrackPoint point, VehicleRow vehicle, Integer plateColor, ZoneId zone) {
        TrackPointVO vo = new TrackPointVO();
        vo.setPlateNo(vehicle.getPlateNo());
        vo.setPlateColorName(ReportConverter.plateColorName(plateColor));
        vo.setDepartmentName(vehicle.getDepartmentName());
        vo.setDeviceTimeText(TIME_FORMATTER.format(point.deviceTime().atZone(zone)));
        vo.setLongitude(BigDecimal.valueOf(point.longitude(), DEGREE_SCALE));
        vo.setLatitude(BigDecimal.valueOf(point.latitude(), DEGREE_SCALE));
        vo.setSpeedKph(BigDecimal.valueOf(point.speed(), ONE_DECIMAL_SCALE));
        vo.setRecorderSpeedKph(BigDecimal.valueOf(point.recorderSpeed(), ONE_DECIMAL_SCALE));
        vo.setMileage(BigDecimal.valueOf(point.mileage(), ONE_DECIMAL_SCALE));
        vo.setOil(BigDecimal.valueOf(point.oil(), ONE_DECIMAL_SCALE));
        vo.setDirectionText(directionText(point.course()));
        vo.setAltitude(point.altitude());
        vo.setStatusText(statusText(point.status()));
        vo.setAlarmFlagText(alarmFlagText(point.alarmFlag()));
        vo.setValidText((point.status() & BIT_LOCATED) != 0 ? "有效" : "无效");
        return vo;
    }

    public static TrackResultVO toResultVO(VehicleRow vehicle, long total, List<TrackPointVO> points) {
        TrackResultVO vo = new TrackResultVO();
        vo.setVehicleId(vehicle.getId());
        vo.setPlateNo(vehicle.getPlateNo());
        vo.setSimNo(vehicle.getSimNo());
        vo.setDepartmentName(vehicle.getDepartmentName());
        vo.setTotal(total);
        vo.setPoints(points);
        return vo;
    }

    private static String statusText(int status) {
        String acc = (status & BIT_ACC) != 0 ? "ACC开" : "ACC关";
        String located = (status & BIT_LOCATED) != 0 ? "定位" : "未定位";
        String running = (status & BIT_STOPPED) != 0 ? "停运" : "运营";
        return acc + "," + located + "," + running;
    }

    private static String directionText(int course) {
        int direction = Math.floorMod(course, DEGREE_FULL_CIRCLE);
        if (direction == 0) {
            return "正北";
        }
        if (direction == 45) {
            return "东北";
        }
        if (direction == 90) {
            return "正东";
        }
        if (direction == 135) {
            return "东南";
        }
        if (direction == 180) {
            return "正南";
        }
        if (direction == 225) {
            return "西南";
        }
        if (direction == 270) {
            return "正西";
        }
        if (direction == 315) {
            return "西北";
        }
        if (direction < 90) {
            return "东偏北" + direction + "度";
        }
        if (direction < 180) {
            return "东偏南" + (direction - 90) + "度";
        }
        if (direction < 270) {
            return "西偏南" + (270 - direction) + "度";
        }
        return "西偏北" + (direction - 270) + "度";
    }

    private static String alarmFlagText(int alarmFlag) {
        return alarmFlag == 0 ? "无报警" : String.format("0x%08X", alarmFlag);
    }
}
