package com.cm.admin.converter;

import com.cm.admin.vo.AlarmNotificationVO;
import com.cm.admin.vo.AlarmRecordVO;
import com.cm.infrastructure.persistence.row.AlarmRow;

/**
 * 报警通知行的 VO 转换。类型 / 来源 / 颜色文案复用报表域同一份码表，
 * 避免弹窗与报表对同一报警给出两种叫法。
 */
public final class AlarmViewConverter {

    private AlarmViewConverter() {
    }

    public static AlarmNotificationVO toNotificationVO(AlarmRow row) {
        AlarmNotificationVO vo = new AlarmNotificationVO();
        vo.setId(row.getId());
        vo.setPlateNo(row.getPlateNo());
        vo.setPlateColorName(ReportConverter.plateColorName(row.getPlateColor()));
        vo.setSimNo(row.getSimNo() == null ? "" : row.getSimNo());
        vo.setAlarmTypeName(ReportConverter.alarmTypeName(row.getAlarmType()));
        vo.setAlarmSourceName(ReportConverter.alarmSourceName(row.getAlarmSource()));
        vo.setLocation(row.getLocation() == null ? "" : row.getLocation());
        vo.setStartTimeText(ReportConverter.timeText(row.getStartTime()));
        vo.setProcessed(Boolean.TRUE.equals(row.getProcessed()));
        vo.setVehicleId(row.getVehicleId());
        vo.setLatitude(row.getLatitude());
        vo.setLongitude(row.getLongitude());
        return vo;
    }

    /**
     * 报警处理页行：在弹窗字段基础上补报警速度、持续时长、处理人与备注。
     * 处理页不弹窗、不播声音，所以不重复计算 alarmTypeDescr 那套拼接文案。
     */
    public static AlarmRecordVO toRecordVO(AlarmRow row) {
        AlarmRecordVO vo = new AlarmRecordVO();
        vo.setId(row.getId());
        vo.setPlateNo(row.getPlateNo());
        vo.setPlateColorName(ReportConverter.plateColorName(row.getPlateColor()));
        vo.setSimNo(row.getSimNo() == null ? "" : row.getSimNo());
        vo.setAlarmTypeName(ReportConverter.alarmTypeName(row.getAlarmType()));
        vo.setAlarmSourceName(ReportConverter.alarmSourceName(row.getAlarmSource()));
        vo.setDescription(row.getDescription() == null ? "" : row.getDescription());
        vo.setLocation(row.getLocation() == null ? "" : row.getLocation());
        vo.setStartTimeText(ReportConverter.timeText(row.getStartTime()));
        vo.setVelocity(row.getVelocity());
        vo.setTimeSpan(row.getTimeSpan());
        vo.setProcessed(Boolean.TRUE.equals(row.getProcessed()));
        vo.setProcessedUserName(row.getProcessedUserName() == null ? "" : row.getProcessedUserName());
        vo.setProcessedTimeText(ReportConverter.timeText(row.getProcessedTime()));
        vo.setRemark(row.getRemark() == null ? "" : row.getRemark());
        vo.setVehicleId(row.getVehicleId());
        vo.setLatitude(row.getLatitude());
        vo.setLongitude(row.getLongitude());
        return vo;
    }
}
