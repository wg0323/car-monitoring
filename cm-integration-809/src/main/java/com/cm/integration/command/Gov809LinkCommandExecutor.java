package com.cm.integration.command;

import com.cm.domain.command.GovLinkCommand;
import com.cm.infrastructure.gov809.GovCommandStatus;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.service.CmJt809CommandService;
import com.cm.integration.config.Gov809Properties;
import com.cm.integration.link.Gov809LinkManager;
import com.cm.integration.link.Jt809UpstreamClient;
import com.cm.protocol.jt809.Jt809Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 809 链路命令执行器。管理端只写库，本进程是唯一持有 809 连接的一方：
 * 定时抢占「待下发」并执行，结果回写同一行。链路不可用时直接置失败，
 * 不排队等待重连——旧系统在未连接时同样不发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Gov809LinkCommandExecutor {

    private static final int BATCH_SIZE = 20;

    /** 执行中超时窗口：抢占后进程被杀或回写异常，超过该时长仍停在执行中就回收为失败。 */
    private static final int STALE_MINUTES = 5;

    private static final byte[] EMPTY_BODY = new byte[0];

    private final CmJt809CommandService commandService;

    private final Jt809UpstreamClient upstreamClient;

    private final Gov809LinkManager linkManager;

    private final Gov809Properties properties;

    private final Gov809CommandService govCommandService;

    @Scheduled(initialDelayString = "${cm.gov809.link-command.initial-delay-millis:5000}",
            fixedDelayString = "${cm.gov809.link-command.interval-millis:2000}")
    public void runOnce() {
        if (!properties.isEnabled()) {
            return;
        }
        commandService.recoverStaleExecution(STALE_MINUTES);
        for (CmJt809Command row : commandService.listPending(BATCH_SIZE)) {
            if (!commandService.claim(row.getId())) {
                continue;
            }
            boolean sent;
            try {
                sent = execute(row);
            } catch (RuntimeException ex) {
                log.warn("809 下行命令执行异常, id={}, cmd=0x{}", row.getId(), hex(row.getCmd()), ex);
                sent = false;
            }
            String descr = describe(row, sent);
            commandService.markResult(row.getId(),
                    sent ? GovCommandStatus.SENT : GovCommandStatus.FAILED, descr);
            log.info("809 下行命令已执行, id={}, cmd=0x{}, sent={}", row.getId(), hex(row.getCmd()), sent);
        }
    }

    /**
     * 报警督办应答（0x1400/0x1401）与链路命令共用同一条待下发队列，按子类型分流；
     * 结果码与车牌已经由管理端写入行内，这里只负责编码并写入链路。
     */
    private boolean execute(CmJt809Command row) {
        if (isUrgeTodoAck(row)) {
            String[] parts = row.getCmdData() == null ? new String[0] : row.getCmdData().split(";");
            if (parts.length != 2) {
                return false;
            }
            long superviseId = Long.parseLong(parts[0].trim());
            int result = Integer.parseInt(parts[1].trim());
            int plateColor = row.getPlateColor() == null ? 0 : row.getPlateColor();
            return govCommandService.sendUrgeTodoAckBody(row.getPlateNo(), plateColor, superviseId, result);
        }
        return executeLinkCommand(row.getCmd());
    }

    private boolean isUrgeTodoAck(CmJt809Command row) {
        return row.getCmd() != null && row.getCmd() == Jt809Constants.MSG_WARN_URGE_TODO
                && row.getSubCmd() != null && row.getSubCmd() == Jt809Constants.SUB_TYPE_URGE_TODO_ACK;
    }

    private String describe(CmJt809Command row, boolean sent) {
        if (isUrgeTodoAck(row)) {
            // 失败时报文已落 outbox，链路重连后补发；描述不能写成“彻底失败”让页面误判。
            return "[0x1401]报警督办应答" + (sent ? "：已写入链路" : "：链路不可用（重连后补发）");
        }
        return resultDescr(row.getCmd(), sent);
    }

    /**
     * 0x1001 在未连接时只能确认「已发起连接」，登录结果要等 0x1002 与链路状态，
     * 不能笼统写「发送成功」让页面看起来像链路已通。
     */
    private String resultDescr(int linkCmd, boolean sent) {
        if (!sent) {
            return GovLinkCommand.describe(linkCmd) + "：链路不可用";
        }
        if (linkCmd == GovLinkCommand.CONNECT_MAIN_LINK) {
            return GovLinkCommand.describe(linkCmd) + "：已发起连接，登录结果见链路状态";
        }
        return GovLinkCommand.describe(linkCmd);
    }

    /**
     * 0x1007 与 0x1008 必须从从链路发出，不能降级到主链路。
     */
    private boolean executeLinkCommand(int linkCmd) {
        return switch (linkCmd) {
            case GovLinkCommand.CONNECT_MAIN_LINK -> upstreamClient.requestMainLink();
            case GovLinkCommand.LOGOUT_MAIN_LINK -> upstreamClient.requestLogout();
            case GovLinkCommand.NOTIFY_MAIN_LINK_DISCONNECTED,
                    GovLinkCommand.NOTIFY_CLOSE_LINK -> linkManager.sendFromSubLink(linkCmd, EMPTY_BODY);
            default -> false;
        };
    }

    private String hex(Integer value) {
        return value == null ? "" : Integer.toHexString(value);
    }
}
