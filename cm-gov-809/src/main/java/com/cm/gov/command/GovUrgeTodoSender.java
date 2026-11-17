package com.cm.gov.command;

import com.cm.gov.link.GovLinkManager;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.body.UrgeTodoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 监管侧下发报警督办 0x1400。下级收不收在应答 0x1401 里体现，
 * 这里只返回“已写入链路”，语义与旧实现一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovUrgeTodoSender {

    private final GovLinkManager linkManager;

    private final Jt809CommandLogService commandLogService;

    public boolean sendUrgeTodo(long platformId, UrgeTodoRequest request) {
        boolean sent = linkManager.sendToLower(platformId, Jt809Constants.MSG_WARN_URGE_TODO,
                request.encode());
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(platformId);
        row.setSn(0);
        row.setCmd(Jt809Constants.MSG_WARN_URGE_TODO);
        row.setSubCmd(Jt809Constants.SUB_TYPE_URGE_TODO_REQUEST);
        row.setCmdData(request.superviseId() + ";" + request.supervisionLevel());
        row.setDescr("[0x1400]报警督办请求");
        row.setPlateNo(request.plateNo());
        row.setPlateColor(request.plateColor());
        row.setSimNo("");
        row.setSource("SEND");
        row.setCommandStatus(sent ? "发送成功" : "发送失败");
        row.setUserId(0L);
        row.setCompanyId(0L);
        commandLogService.log(row);
        log.info("809 督办请求下发, platformId={}, superviseId={}, sent={}",
                platformId, request.superviseId(), sent);
        return sent;
    }
}
 
