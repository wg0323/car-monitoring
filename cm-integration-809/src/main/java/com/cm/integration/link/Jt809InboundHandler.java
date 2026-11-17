package com.cm.integration.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.infrastructure.gov809.GovCommandStatus;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.gov809.WarnMsgUrgeTodoService;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import com.cm.integration.config.Gov809Properties;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Frame;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import com.cm.protocol.jt809.body.SubLinkLogin;
import com.cm.protocol.jt809.body.SubLinkLoginAck;
import com.cm.protocol.jt809.body.UrgeTodoRequest;
import com.cm.transport.Jt809LinkSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 企业侧 809 入站分发。应答一律从收到请求的通道发回（从链路请求只能从从链路应答），
 * 业务报文则走 LinkManager 的主链路优先策略。
 */
@Slf4j
@RequiredArgsConstructor
public class Jt809InboundHandler extends SimpleChannelInboundHandler<Jt809Frame> {

    private static final HexFormat HEX = HexFormat.of();

    /** cm_jt809_command.cmd_data 为 VARCHAR(255)，留 5 个字符余量。 */
    private static final int CMD_DATA_MAX_LENGTH = 250;

    private final Gov809LinkManager linkManager;

    private final WarnMsgUrgeTodoService warnTodoService;

    private final Jt809CommandLogService commandLogService;

    private final CmVehicleMapper vehicleMapper;

    private final Gov809Properties properties;

    private final ThreadPoolExecutor businessExecutor;

    private final boolean mainChannel;

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        Channel channel = context.channel();
        boolean main = mainChannel;
        // 状态落库不能在 EventLoop 上做，否则一次 DB 卡顿会把整条链路拖住。
        businessExecutor.execute(() -> {
            if (main) {
                linkManager.onMainClosed(channel);
            } else {
                linkManager.onSubClosed(channel);
            }
        });
    }

    /** 读空闲就断链：主链路断开后由 connector 重连，从链路等上级重新发起。 */
    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) {
        if (event instanceof IdleStateEvent) {
            log.warn("809 链路读空闲超时，主动断开, main={}", mainChannel);
            context.close();
            return;
        }
        context.fireUserEventTriggered(event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        log.warn("809 链路异常关闭, main={}, reason={}", mainChannel, cause.toString());
        context.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Jt809Frame frame) {
        switch (frame.messageId()) {
            case Jt809Constants.MSG_MAIN_LINK_LOGIN_ACK -> handleLoginAck(context, frame);
            case Jt809Constants.MSG_MAIN_LINK_LOGOUT,
                    Jt809Constants.MSG_MAIN_LINK_LOGOUT_ACK,
                    Jt809Constants.MSG_SUB_LINK_LOGOUT_ACK -> context.close();
            case Jt809Constants.MSG_MAIN_LINK_TEST_ACK -> log.debug("809 主链路心跳应答");
            case Jt809Constants.MSG_SUB_LINK_LOGIN -> handleSubLinkLogin(context, frame);
            case Jt809Constants.MSG_SUB_LINK_LOGIN_ACK -> businessExecutor.execute(() ->
                    linkManager.onSubLoginSuccess());
            case Jt809Constants.MSG_SUB_LINK_LOGOUT -> {
                reply(context.channel(), Jt809Constants.MSG_SUB_LINK_LOGOUT_ACK, new byte[0]);
                context.close();
            }
            case Jt809Constants.MSG_SUB_LINK_TEST ->
                    reply(context.channel(), Jt809Constants.MSG_SUB_LINK_TEST_ACK, new byte[0]);
            case Jt809Constants.MSG_WARN_URGE_TODO -> handleUrgeTodo(frame);
            case Jt809Constants.MSG_VEHICLE_LOCATION ->
                    log.debug("809 企业侧收到位置报文，本端不处理, length={}", frame.bodyLength());
            default -> {
                log.warn("809 未处理的消息, msgId=0x{}", Integer.toHexString(frame.messageId()));
                businessExecutor.execute(() -> logInbound(frame));
            }
        };
    }

    private void handleLoginAck(ChannelHandlerContext context, Jt809Frame frame) {
        MainLinkLoginAck ack = MainLinkLoginAck.parse(frame.body());
        if (ack.result() != MainLinkLoginAck.RESULT_SUCCESS) {
            log.error("809 主链路登录被拒, result={}, verifyCode={}", ack.result(), ack.verifyCode());
            businessExecutor.execute(() -> logCommand(frame, "RECEIVE", "失败", "", 0,
                    String.valueOf(ack.result())));
            context.close();
            return;
        }
        businessExecutor.execute(() -> {
            logCommand(frame, "RECEIVE", "接收成功", "", 0, String.valueOf(ack.verifyCode()));
            linkManager.onMainLoginSuccess();
        });
    }

    private void handleSubLinkLogin(ChannelHandlerContext context, Jt809Frame frame) {
        SubLinkLogin login = SubLinkLogin.parse(frame.body());
        reply(context.channel(), Jt809Constants.MSG_SUB_LINK_LOGIN_ACK,
                new SubLinkLoginAck(SubLinkLoginAck.RESULT_SUCCESS).encode());
        businessExecutor.execute(() -> {
            logCommand(frame, "RECEIVE", "接收成功", "", 0, String.valueOf(login.verifyCode()));
            linkManager.onSubLoginSuccess();
        });
    }

    /**
     * 督办请求落库后不自动应答：答复结果码是人工处置结论，旧系统也由界面触发，
     * 自动回 0（处理成功）会把业务语义改错。
     */
    private void handleUrgeTodo(Jt809Frame frame) {
        businessExecutor.execute(() -> {
            UrgeTodoRequest request = UrgeTodoRequest.parse(frame.body());
            CmWarnMsgUrgeTodoReq row = toEntity(request);
            boolean created = warnTodoService.record(row);
            logCommand(frame, "RECEIVE", created ? "接收成功" : "重复接收", request.plateNo(),
                    request.plateColor(), String.valueOf(request.superviseId()));
            log.info("809 督办请求已落库, superviseId={}, plateNo={}, created={}",
                    request.superviseId(), request.plateNo(), created);
        });
    }

    private CmWarnMsgUrgeTodoReq toEntity(UrgeTodoRequest request) {
        ZoneId zone = ZoneId.of(properties.getZone());
        CmWarnMsgUrgeTodoReq row = new CmWarnMsgUrgeTodoReq();
        // 统一用本端接入码：帧头的接入码在本端方向恒为本平台编码，不是来源平台编码；
        // 应答更新也按本端接入码定位，两边口径必须一致，否则督办单永远停在未应答。
        row.setPlatformId(properties.getPlatformId());
        row.setSuperviseId(request.superviseId());
        row.setPlateNo(request.plateNo());
        row.setPlateColor(request.plateColor());
        row.setWarnSrc(request.warnSource());
        row.setWarnType(request.warnType());
        row.setWarnTime(toLocalDateTime(request.warnTimeEpochSecond(), zone));
        row.setSupervisionEndTime(toLocalDateTime(request.superviseEndEpochSecond(), zone));
        row.setSupervisionLevel(request.supervisionLevel());
        row.setSupervisor(request.supervisor());
        row.setSupervisorTel(request.supervisorTel());
        row.setSupervisorEmail(request.supervisorEmail());
        row.setAckResult(0);
        CmVehicle vehicle = findVehicle(request.plateNo());
        row.setVehicleId(vehicle == null || vehicle.getId() == null ? 0L : vehicle.getId());
        row.setCompanyId(vehicle == null || vehicle.getCompanyId() == null ? 0L : vehicle.getCompanyId());
        return row;
    }

    private CmVehicle findVehicle(String plateNo) {
        try {
            return vehicleMapper.selectOne(Wrappers.<CmVehicle>lambdaQuery()
                    .eq(CmVehicle::getPlateNo, plateNo)
                    .last("LIMIT 1"));
        } catch (RuntimeException ex) {
            log.warn("督办车牌反查车辆失败, 按未绑定处理, plateNo={}", plateNo, ex);
            return null;
        }
    }

    /**
     * 上级通过从链路下发的业务报文（查岗、平台间报文等）统一落一条接收记录，
     * 页面「上级政府平台」才能看到对方下发过什么。正文只留前 250 字符十六进制，
     * 不解析尚未落地的报文结构，避免猜错字段。
     */
    private void logInbound(Jt809Frame frame) {
        String name = Jt809Constants.describe(frame.messageId());
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(properties.getPlatformId());
        row.setSn((int) frame.messageSn());
        row.setCmd(frame.messageId());
        row.setSubCmd(0);
        row.setCmdData(hexPreview(frame.body()));
        row.setDescr("[" + (name.isEmpty() ? "上级下发报文" : name) + "]");
        row.setPlateNo("");
        row.setPlateColor(0);
        row.setSimNo("");
        row.setSource("RECEIVE");
        row.setCommandStatus(GovCommandStatus.RECEIVED);
        row.setUserId(0L);
        row.setCompanyId(0L);
        commandLogService.log(row);
    }

    private String hexPreview(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String hex = HEX.formatHex(body);
        return hex.length() <= CMD_DATA_MAX_LENGTH ? hex : hex.substring(0, CMD_DATA_MAX_LENGTH);
    }

    private void logCommand(Jt809Frame frame, String source, String status, String plateNo,
                            int plateColor, String data) {
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(properties.getPlatformId());
        row.setSn((int) frame.messageSn());
        row.setCmd(frame.messageId());
        row.setSubCmd(0);
        row.setCmdData(data);
        // 描述带上消息中文名，页面「类型描述」列才看得懂；未登记的消息号回退到十六进制。
        String name = Jt809Constants.describe(frame.messageId());
        row.setDescr("[" + (name.isEmpty() ? "0x" + Integer.toHexString(frame.messageId()) : name) + "]" + status);
        row.setPlateNo(plateNo);
        row.setPlateColor(plateColor);
        row.setSimNo("");
        row.setSource(source);
        row.setCommandStatus(status);
        row.setUserId(0L);
        row.setCompanyId(0L);
        commandLogService.log(row);
    }

    private void reply(Channel channel, int messageId, byte[] body) {
        Jt809LinkSession.writeNoWait(channel, linkManager.session().encode(messageId, body));
    }

    private static LocalDateTime toLocalDateTime(long epochSecond, ZoneId zone) {
        return epochSecond == 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), zone);
    }
}
 
