package com.cm.integration.command;

import com.cm.infrastructure.gov809.Gov809OutboxMessage;
import com.cm.infrastructure.gov809.Gov809OutboxService;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.gov809.WarnMsgUrgeTodoService;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.integration.config.Gov809Properties;
import com.cm.integration.link.Gov809LinkManager;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.body.UrgeTodoAck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HexFormat;

/**
 * 809 下行业务报文。当前只有报警督办应答：结果码是人工处置结论，
 * 所以由调用方（P4 的界面操作）传入，不在收到 0x1400 时自动回。
 * 链路不可用时写入积压队列，重连后补发，同时把待发状态落到指令日志供页面展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Gov809CommandService {

    private static final HexFormat HEX = HexFormat.of();

    private final Gov809LinkManager linkManager;

    private final Gov809OutboxService outboxService;

    private final WarnMsgUrgeTodoService warnTodoService;

    private final Jt809CommandLogService commandLogService;

    private final Gov809Properties properties;

    public boolean sendUrgeTodoAck(String plateNo, int plateColor, long superviseId, int result) {
        boolean sent = sendUrgeTodoAckBody(plateNo, plateColor, superviseId, result);
        logCommand(plateNo, plateColor, superviseId, result, sent);
        log.info("809 督办应答, superviseId={}, result={}, sent={}", superviseId, result, sent);
        return sent;
    }

    /**
     * 只发送并推进督办状态，不写指令日志。链路执行器抢占的是管理端写入的
     * 待下发记录，审计已有归属，不能在这里再写一条。
     */
    public boolean sendUrgeTodoAckBody(String plateNo, int plateColor, long superviseId, int result) {
        byte[] body = new UrgeTodoAck(plateNo, plateColor, superviseId, result).encode();
        boolean sent = linkManager.send(Jt809Constants.MSG_WARN_URGE_TODO, body);
        if (sent) {
            warnTodoService.acknowledge(properties.getPlatformId(), superviseId, result);
        } else {
            outboxService.append(new Gov809OutboxMessage(properties.getPlatformId(),
                    "URGE-ACK-" + properties.getPlatformId() + "-" + superviseId,
                    Jt809Constants.MSG_WARN_URGE_TODO, Jt809Constants.SUB_TYPE_URGE_TODO_ACK,
                    plateNo, plateColor, HEX.formatHex(body)));
        }
        return sent;
    }

    private void logCommand(String plateNo, int plateColor, long superviseId, int result, boolean sent) {
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(properties.getPlatformId());
        row.setSn(0);
        row.setCmd(Jt809Constants.MSG_WARN_URGE_TODO);
        row.setSubCmd(Jt809Constants.SUB_TYPE_URGE_TODO_ACK);
        row.setCmdData(superviseId + ";" + result);
        row.setDescr("[0x1401]报警督办应答");
        row.setPlateNo(plateNo);
        row.setPlateColor(plateColor);
        row.setSimNo("");
        row.setSource("SEND");
        row.setCommandStatus(sent ? "发送成功" : "待补发");
        row.setUserId(0L);
        row.setCompanyId(0L);
        commandLogService.log(row);
    }
}
 
