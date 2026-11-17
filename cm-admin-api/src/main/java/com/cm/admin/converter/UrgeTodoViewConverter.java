package com.cm.admin.converter;

import com.cm.admin.vo.UrgeTodoVO;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;

/**
 * 809 督办行的 VO 转换。报警来源与报警类型按 JT/T 809 的协议码表翻译，
 * 未登记的码值原样输出数字，便于排障时看到真实取值。
 */
public final class UrgeTodoViewConverter {

    private UrgeTodoViewConverter() {
    }

    public static UrgeTodoVO toVO(CmWarnMsgUrgeTodoReq entity) {
        UrgeTodoVO vo = new UrgeTodoVO();
        vo.setId(entity.getId());
        vo.setSuperviseId(entity.getSuperviseId());
        vo.setPlateNo(entity.getPlateNo());
        vo.setPlateColorName(ReportConverter.plateColorName(entity.getPlateColor()));
        vo.setWarnSourceText(warnSourceText(entity.getWarnSrc()));
        vo.setWarnTypeText(warnTypeText(entity.getWarnType()));
        vo.setWarnTimeText(ReportConverter.timeText(entity.getWarnTime()));
        vo.setSupervisionEndTimeText(ReportConverter.timeText(entity.getSupervisionEndTime()));
        vo.setSupervisionLevelText(supervisionLevelText(entity.getSupervisionLevel()));
        vo.setSupervisor(entity.getSupervisor() == null ? "" : entity.getSupervisor());
        vo.setSupervisorTel(entity.getSupervisorTel() == null ? "" : entity.getSupervisorTel());
        vo.setSupervisorEmail(entity.getSupervisorEmail() == null ? "" : entity.getSupervisorEmail());
        vo.setAcknowledged(entity.getAckTime() != null);
        vo.setAckResult(entity.getAckTime() == null ? null : entity.getAckResult());
        vo.setAckResultText(ackResultText(entity.getAckTime() == null ? null : entity.getAckResult()));
        vo.setAckTimeText(ReportConverter.timeText(entity.getAckTime()));
        vo.setCreateTimeText(ReportConverter.timeText(entity.getCreateTime()));
        return vo;
    }

    static String warnSourceText(Integer warnSource) {
        if (warnSource == null) {
            return "";
        }
        return switch (warnSource) {
            case 1 -> "车载终端";
            case 2 -> "企业监控平台";
            case 3 -> "政府监控平台";
            default -> String.valueOf(warnSource);
        };
    }

    static String warnTypeText(Integer warnType) {
        if (warnType == null) {
            return "";
        }
        return switch (warnType) {
            case 1 -> "超速";
            case 2 -> "疲劳驾驶";
            case 3 -> "紧急报警";
            case 4 -> "事故";
            case 5 -> "故障";
            case 6 -> "违规行驶";
            case 7 -> "其他";
            default -> String.valueOf(warnType);
        };
    }

    /** 旧系统口径：0 紧急，其余一律「一般」。 */
    static String supervisionLevelText(Integer supervisionLevel) {
        return supervisionLevel != null && supervisionLevel == 0 ? "紧急" : "一般";
    }

    /**
     * 结果码与旧系统督办应答下拉一致；null 表示还没应答。
     */
    static String ackResultText(Integer ackResult) {
        if (ackResult == null) {
            return "未应答";
        }
        return switch (ackResult) {
            case 0 -> "处理中";
            case 1 -> "已处理完毕";
            case 2 -> "不做处理";
            case 3 -> "将来处理";
            default -> String.valueOf(ackResult);
        };
    }
}
