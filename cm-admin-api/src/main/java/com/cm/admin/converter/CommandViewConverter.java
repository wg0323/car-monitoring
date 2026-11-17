package com.cm.admin.converter;

import com.cm.admin.vo.CommandVO;
import com.cm.admin.vo.CommandVehicleVO;
import com.cm.admin.vo.GovCommandVO;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.row.VehicleRow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 指令域的持久层对象到 API VO 转换。状态原值与中文文案的映射集中在这里，
 * 避免前端各自维护一份码表。
 */
public final class CommandViewConverter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String POSITION_QUERY_CODE = "0x8201";

    private static final String SOURCE_SEND = "SEND";

    private CommandViewConverter() {
    }

    public static CommandVO toCommandVO(CmTerminalCommand entity) {
        CommandVO vo = new CommandVO();
        vo.setCommandId(entity.getCommandId());
        vo.setVehicleId(entity.getVehicleId());
        vo.setPlateNo(entity.getPlateNo());
        vo.setSimNo(entity.getSimNo());
        vo.setCommandCode(entity.getCommandCode());
        vo.setCommandName(commandName(entity.getCommandCode()));
        vo.setCommandData(entity.getCommandData());
        vo.setCommandStatus(entity.getCommandStatus());
        vo.setStatusText(statusText(entity.getCommandStatus()));
        vo.setSentAtText(format(entity.getSentAt()));
        vo.setAckAtText(format(entity.getAckAt()));
        vo.setCreateTimeText(format(entity.getCreateTime()));
        vo.setRemark(entity.getRemark());
        return vo;
    }

    public static CommandVehicleVO toCommandVehicleVO(VehicleRow row, boolean online) {
        CommandVehicleVO vo = new CommandVehicleVO();
        vo.setId(row.getId());
        vo.setPlateNo(row.getPlateNo());
        vo.setSimNo(row.getSimNo());
        vo.setOnline(online);
        return vo;
    }

    public static GovCommandVO toGovCommandVO(CmJt809Command entity) {
        GovCommandVO vo = new GovCommandVO();
        vo.setId(entity.getId());
        vo.setPlatformId(entity.getPlatformId());
        vo.setSn(entity.getSn());
        vo.setCmd(entity.getCmd());
        vo.setCmdText(cmdText(entity.getCmd()));
        vo.setSubCmd(entity.getSubCmd());
        vo.setDescr(entity.getDescr());
        vo.setPlateNo(entity.getPlateNo());
        vo.setPlateColor(entity.getPlateColor());
        vo.setCmdData(entity.getCmdData());
        vo.setSource(entity.getSource());
        vo.setSourceText(sourceText(entity.getSource()));
        vo.setCommandStatus(entity.getCommandStatus());
        vo.setCreateTimeText(format(entity.getCreateTime()));
        vo.setUpdateTimeText(format(entity.getUpdateTime()));
        return vo;
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : DATE_TIME.format(time);
    }

    private static String commandName(String commandCode) {
        String code = commandCode == null ? "" : commandCode.trim().toLowerCase(Locale.ROOT);
        return POSITION_QUERY_CODE.equals(code) ? "点名" : "";
    }

    /**
     * 状态机原值到页面文案。未登记的取值原样返回，便于排障时看到真实状态。
     */
    private static String statusText(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "ACCEPTED" -> "已受理";
            case "SENT_MQ" -> "已发 MQ";
            case "SENT_TERMINAL" -> "已发终端";
            case "ACKED" -> "终端已应答";
            case "SUCCESS" -> "成功";
            case "FAILED" -> "失败";
            case "TIMEOUT" -> "超时";
            default -> status;
        };
    }

    private static String cmdText(Integer cmd) {
        return cmd == null ? "" : "0x" + Integer.toHexString(cmd);
    }

    private static String sourceText(String source) {
        if (source == null) {
            return "";
        }
        return SOURCE_SEND.equals(source) ? "本端下发" : "上级下发";
    }
}
