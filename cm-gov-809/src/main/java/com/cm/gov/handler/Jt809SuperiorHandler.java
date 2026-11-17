package com.cm.gov.handler;

import com.cm.gov.config.GovSuperiorProperties;
import com.cm.gov.link.GovLinkManager;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Frame;
import com.cm.protocol.jt809.body.MainLinkLogin;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import com.cm.protocol.jt809.body.VehicleLocation;
import com.cm.protocol.jt809.body.VehicleLocationBatch;
import com.cm.transport.Jt809LinkSession;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 监管侧 809 入站分发。主链路（下级连入）与从链路（本端回连）共用一套分发：
 * 固定 platformId 的就是从链路，否则从帧头的接入码解析。登录校验与状态落库都放业务线程池，
 * EventLoop 只做解码与写回应答。
 */
@Slf4j
@RequiredArgsConstructor
public class Jt809SuperiorHandler extends SimpleChannelInboundHandler<Jt809Frame> {

    private static final String RECEIVED_POSITIONS = "cm.gov809.received.positions";

    private final Long fixedPlatformId;

    private final GovLinkManager linkManager;

    private final Jt809CommandLogService commandLogService;

    private final MeterRegistry meterRegistry;

    private final GovSuperiorProperties properties;

    private final ThreadPoolExecutor businessExecutor;

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        Long platformId = platformId(context.channel());
        if (platformId == null) {
            return;
        }
        boolean inbound = fixedPlatformId == null;
        Channel channel = context.channel();
        // 状态落库不能在 EventLoop 上做。
        businessExecutor.execute(() -> {
            if (inbound) {
                linkManager.onInboundClosed(platformId, channel);
            } else {
                linkManager.onSubClosed(platformId, channel);
            }
        });
    }

    /** 读空闲就断链，半开连接不能靠业务层重连兜底。 */
    @Override
    public void userEventTriggered(ChannelHandlerContext context, Object event) {
        if (event instanceof IdleStateEvent) {
            log.warn("809 下级链路读空闲超时，主动断开, platformId={}",
                    platformId(context.channel()));
            context.close();
            return;
        }
        context.fireUserEventTriggered(event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        log.warn("809 下级链路异常关闭, platformId={}, reason={}", platformId(context.channel()),
                cause.toString());
        context.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Jt809Frame frame) {
        switch (frame.messageId()) {
            case Jt809Constants.MSG_MAIN_LINK_LOGIN -> handleLogin(context, frame);
            case Jt809Constants.MSG_MAIN_LINK_TEST ->
                    reply(context.channel(), frame.centerId(), Jt809Constants.MSG_MAIN_LINK_TEST_ACK,
                            new byte[0]);
            case Jt809Constants.MSG_MAIN_LINK_LOGOUT -> {
                reply(context.channel(), frame.centerId(), Jt809Constants.MSG_MAIN_LINK_LOGOUT_ACK,
                        new byte[0]);
                context.close();
            }
            case Jt809Constants.MSG_SUB_LINK_LOGIN_ACK -> {
                Long platformId = platformId(context.channel());
                if (platformId != null) {
                    businessExecutor.execute(() -> linkManager.onSubLoggedIn(platformId));
                }
            }
            case Jt809Constants.MSG_SUB_LINK_LOGOUT_ACK, Jt809Constants.MSG_SUB_LINK_TEST_ACK -> {
                // 从链路注销应答与心跳应答无需处理。
            }
            case Jt809Constants.MSG_VEHICLE_LOCATION -> countPosition(frame);
            default -> log.debug("809 监管侧未处理的消息, msgId=0x{}",
                    Integer.toHexString(frame.messageId()));
        }
    }

    /**
     * 0x1001 校验通过后回 0x1002，再按登录体里的地址回连下级从链路并发 0x9001。
     * 校验失败只回结果码，不回连，也不把连接标成在线。
     */
    private void handleLogin(ChannelHandlerContext context, Jt809Frame frame) {
        MainLinkLogin login = MainLinkLogin.parse(frame.body());
        long platformId = frame.centerId();
        businessExecutor.execute(() -> {
            MainLinkLoginAck ack = linkManager.validate(platformId, login.userId(), login.password());
            Jt809LinkSession.writeNoWait(context.channel(),
                    linkManager.session(platformId).encode(Jt809Constants.MSG_MAIN_LINK_LOGIN_ACK,
                            ack.encode()));
            logCommand(platformId, frame, ack.result() == MainLinkLoginAck.RESULT_SUCCESS
                    ? "接收成功" : "失败", String.valueOf(ack.result()));
            if (ack.result() != MainLinkLoginAck.RESULT_SUCCESS) {
                log.warn("809 下级平台登录被拒, platformId={}, result={}", platformId, ack.result());
                context.channel().close();
                return;
            }
            context.channel().attr(GovLinkManager.PLATFORM_ID).set(platformId);
            linkManager.bindInbound(platformId, context.channel());
            linkManager.onMainLoggedIn(platformId);
            if (!linkManager.connectSubLink(platformId, login.subLinkIp(), login.subLinkPort())) {
                log.warn("809 回连下级从链路失败，主链路仍可用, platformId={}, {}:{}",
                        platformId, login.subLinkIp(), login.subLinkPort());
            }
        });
    }

    /** 上级只计数不落轨迹：轨迹主库在本平台自己的链路里，不把对端数据重复存一份。 */
    private void countPosition(Jt809Frame frame) {
        int subType = Jt809Bytes.unsignedShort(frame.body(), Jt809Constants.PLATE_LENGTH + 1);
        meterRegistry.counter(RECEIVED_POSITIONS).increment();
        if (subType == Jt809Constants.SUB_TYPE_HISTORY_LOCATION) {
            VehicleLocationBatch batch = VehicleLocationBatch.parse(frame.body());
            meterRegistry.counter(RECEIVED_POSITIONS).increment(batch.samples().size() - 1);
        } else {
            VehicleLocation location = VehicleLocation.parse(frame.body());
            log.debug("809 收到下级定位, plateNo={}, time={}", location.plateNo(),
                    location.sample().deviceTime());
        }
    }

    private void logCommand(long platformId, Jt809Frame frame, String status, String data) {
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(platformId);
        row.setSn((int) frame.messageSn());
        row.setCmd(frame.messageId());
        row.setSubCmd(0);
        row.setCmdData(data);
        row.setDescr("[0x" + Integer.toHexString(frame.messageId()) + "]" + status);
        row.setPlateNo("");
        row.setPlateColor(0);
        row.setSimNo("");
        row.setSource("RECEIVE");
        row.setCommandStatus(status);
        row.setUserId(0L);
        row.setCompanyId(0L);
        commandLogService.log(row);
    }

    private void reply(Channel channel, long platformId, int messageId, byte[] body) {
        Jt809LinkSession.writeNoWait(channel,
                linkManager.session(platformId).encode(messageId, body));
    }

    private Long platformId(Channel channel) {
        if (fixedPlatformId != null) {
            return fixedPlatformId;
        }
        Long platformId = channel.attr(GovLinkManager.PLATFORM_ID).get();
        return platformId == null ? null : platformId;
    }
}
 
