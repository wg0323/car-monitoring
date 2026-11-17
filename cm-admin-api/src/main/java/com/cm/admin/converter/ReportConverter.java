package com.cm.admin.converter;

import com.cm.admin.vo.ReportAlarmVO;
import com.cm.admin.vo.ReportOnlineRecordVO;
import com.cm.admin.vo.ReportOnlineVehicleVO;
import com.cm.admin.vo.ReportOptionVO;
import com.cm.admin.vo.ReportOptionsVO;
import com.cm.infrastructure.monitor.PositionSnapshot;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.persistence.row.OnlineRecordRow;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;
import com.cm.protocol.alarm.AlarmBit;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表行到 VO 的转换。报警类型、来源与车牌颜色都是码值翻译，集中在这里避免页面与导出各写一份。
 */
public final class ReportConverter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String ALARMING_TEXT = "报警中";

    private static final String IN_PROGRESS_TEXT = "进行中";

    private static final String TYPE_PARKING = "Parking";

    private static final String TYPE_ACC = "Acc";

    private static final String TYPE_IN_AREA = "InArea";

    private static final String RESERVED_DESCRIPTION = "保留";

    private ReportConverter() {
    }

    public static ReportAlarmVO toAlarmVO(AlarmRow row) {
        ReportAlarmVO vo = new ReportAlarmVO();
        vo.setPlateNo(row.getPlateNo());
        vo.setPlateColorName(plateColorName(row.getPlateColor()));
        vo.setAlarmTypeName(alarmTypeName(row.getAlarmType()));
        vo.setAlarmSourceName(alarmSourceName(row.getAlarmSource()));
        vo.setLocation(row.getLocation());
        vo.setStartTimeText(timeText(row.getStartTime()));
        vo.setEndTimeText(row.getEndTime() == null ? ALARMING_TEXT : timeText(row.getEndTime()));
        vo.setTimeSpan(row.getTimeSpan());
        vo.setDepartmentName(row.getDepartmentName());
        vo.setVelocity(row.getVelocity());
        return vo;
    }

    public static ReportOnlineRecordVO toOnlineRecordVO(OnlineRecordRow row) {
        ReportOnlineRecordVO vo = new ReportOnlineRecordVO();
        vo.setPlateNo(row.getPlateNo());
        vo.setPlateColorName(plateColorName(row.getPlateColor()));
        vo.setRecordTypeName(recordTypeName(row.getEventType()));
        vo.setLocation(row.getLocation());
        vo.setStartTimeText(timeText(row.getStartTime()));
        vo.setEndTimeText(row.getEndTime() == null ? IN_PROGRESS_TEXT : timeText(row.getEndTime()));
        vo.setTimeSpan(row.getTimeSpan());
        vo.setDepartmentName(row.getDepartmentName());
        return vo;
    }

    /**
     * 在线状态来自 Redis 租约，位置时间来自最新位置。离线时长只对离线且有位置的车辆计算。
     */
    public static ReportOnlineVehicleVO toOnlineVehicleVO(VehicleOnlineReportRow row, boolean online,
                                                          PositionSnapshot snapshot, ZoneId zone,
                                                          LocalDateTime now) {
        ReportOnlineVehicleVO vo = new ReportOnlineVehicleVO();
        vo.setPlateNo(row.getPlateNo());
        vo.setPlateColorName(plateColorName(row.getPlateColor()));
        vo.setSimNo(row.getSimNo());
        vo.setDepartmentName(row.getDepartmentName());
        vo.setOnlineStatusName(online ? "在线" : "离线");
        LocalDateTime positionTime = positionTime(snapshot, zone);
        vo.setOnlineTimeText(positionTime == null ? "" : timeText(positionTime));
        vo.setOfflineMinutes(offlineMinutes(online, positionTime, now));
        vo.setRunStatus(row.getRunStatus());
        vo.setVehicleType(row.getVehicleType());
        vo.setInstallDateText(timeText(row.getInstallDate()));
        return vo;
    }

    public static ReportOptionsVO options() {
        ReportOptionsVO vo = new ReportOptionsVO();
        List<ReportOptionVO> alarmTypes = new ArrayList<>();
        for (AlarmBit bit : AlarmBit.values()) {
            if (!RESERVED_DESCRIPTION.equals(bit.description())) {
                alarmTypes.add(new ReportOptionVO(bit.code(), bit.description()));
            }
        }
        alarmTypes.add(new ReportOptionVO(TYPE_PARKING, "停车"));
        vo.setAlarmTypes(alarmTypes);
        vo.setAlarmSources(List.of(
                new ReportOptionVO("terminal_alarm", "终端报警"),
                new ReportOptionVO("platform_alarm", "平台报警"),
                new ReportOptionVO("gov_alarm", "政府平台报警")));
        vo.setPlateColors(List.of(
                new ReportOptionVO("1", "蓝色"),
                new ReportOptionVO("2", "黄色"),
                new ReportOptionVO("3", "黑色"),
                new ReportOptionVO("4", "白色"),
                new ReportOptionVO("9", "其他")));
        vo.setOnlineRecordTypes(List.of(
                new ReportOptionVO("GpsOnline", "上线记录"),
                new ReportOptionVO("GpsOffline", "下线记录")));
        vo.setOnlineStatuses(List.of(
                new ReportOptionVO("online", "在线"),
                new ReportOptionVO("offline", "离线")));
        return vo;
    }

    public static String timeText(LocalDateTime time) {
        return time == null ? "" : TIME_FORMATTER.format(time);
    }

    public static String plateColorName(Integer color) {
        if (color == null) {
            return "";
        }
        return switch (color) {
            case 1 -> "蓝色";
            case 2 -> "黄色";
            case 3 -> "黑色";
            case 4 -> "白色";
            case 9 -> "其他";
            default -> "未知";
        };
    }

    /**
     * 报警类型码值：终端报警位序号（"0".."31"）、平台停车（Parking）、ACC 与旧面积别名。
     */
    public static String alarmTypeName(String alarmType) {
        if (alarmType == null || alarmType.isBlank()) {
            return "";
        }
        if (TYPE_PARKING.equals(alarmType)) {
            return "停车";
        }
        if (TYPE_ACC.equals(alarmType)) {
            return "ACC 熄火";
        }
        if (TYPE_IN_AREA.equals(alarmType)) {
            return "进出区域";
        }
        try {
            return AlarmBit.descriptionOf(Integer.parseInt(alarmType));
        } catch (NumberFormatException ex) {
            return alarmType;
        }
    }

    public static String alarmSourceName(String alarmSource) {
        if (alarmSource == null || alarmSource.isBlank()) {
            return "";
        }
        return switch (alarmSource) {
            case "terminal_alarm" -> "终端报警";
            case "platform_alarm" -> "平台报警";
            case "gov_alarm" -> "政府平台报警";
            default -> alarmSource;
        };
    }

    private static String recordTypeName(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "";
        }
        return switch (eventType) {
            case "GpsOnline" -> "上线记录";
            case "GpsOffline" -> "下线记录";
            default -> eventType;
        };
    }

    private static LocalDateTime positionTime(PositionSnapshot snapshot, ZoneId zone) {
        if (snapshot == null || snapshot.deviceTime() == null || snapshot.deviceTime().isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(snapshot.deviceTime()), zone);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Long offlineMinutes(boolean online, LocalDateTime positionTime, LocalDateTime now) {
        if (online) {
            return 0L;
        }
        if (positionTime == null) {
            return null;
        }
        long minutes = Duration.between(positionTime, now).toMinutes();
        return Math.max(minutes, 0L);
    }
}
