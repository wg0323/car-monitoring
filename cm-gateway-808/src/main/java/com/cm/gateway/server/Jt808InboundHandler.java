package com.cm.gateway.server;

import com.cm.domain.registration.RegistrationVerdict;
import com.cm.gateway.downlink.CommandAckPublisher;
import com.cm.gateway.downlink.TerminalChannelRegistry;
import com.cm.gateway.telemetry.PositionEventAssembler;
import com.cm.gateway.telemetry.PositionReportDispatcher;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.CommandAckEvent;
import com.cm.infrastructure.command.TerminalRoute;
import com.cm.infrastructure.command.TerminalRouteRegistry;
import com.cm.infrastructure.service.TerminalRegistrationService;
import com.cm.infrastructure.telemetry.OnlineLeaseStore;
import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import com.cm.protocol.Jt808Decoder;
import com.cm.protocol.Jt808Encoder;
import com.cm.protocol.Jt808Message;
import com.cm.protocol.body.AuthenticationRequest;
import com.cm.protocol.body.BatchPositionReport;
import com.cm.protocol.body.GenericAck;
import com.cm.protocol.body.PositionQueryAck;
import com.cm.protocol.body.PositionReport;
import com.cm.protocol.body.RegistrationRequest;
import com.cm.transport.ChannelRoute;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JT808 上行分发。注册、鉴权、心跳与协议即时应答在 EventLoop 内同步完成；
 * 只有需要查库的注册判定、以及发布 confirm 等待才切到业务线程池。
 * 定位报文只做解码与事件组装，发布与 confirm 等待交给业务线程池，确认成功后才写 0x8001。
 * 私有 $ 帧落库但不回应答：它不是标准 JT808 报文，对端不会解析 0x8001。
 */
@Slf4j
@RequiredArgsConstructor
public class Jt808InboundHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final int MSG_GENERIC_ACK = 0x0001;

    private static final int MSG_HEARTBEAT = 0x0002;

    private static final int MSG_REGISTRATION = 0x0100;

    private static final int MSG_AUTHENTICATION = 0x0102;

    private static final int MSG_DEREGISTRATION = 0x0003;

    private static final int MSG_POSITION = 0x0200;

    private static final int MSG_POSITION_QUERY_ACK = 0x0201;

    private static final int MSG_BATCH_POSITION = 0x0704;

    private static final byte PRIVATE_FRAME_PREFIX = 0x24;

    private static final int ACK_RESULT_SUCCESS = 0;

    private static final int ACK_RESULT_FAILURE = 1;

    private static final int ACK_RESULT_MESSAGE_ERROR = 2;

    private static final int SERIAL_MASK = 0xFFFF;

    private static final int RAW_HEX_MAX_LENGTH = 512;

    private static final long LOG_INTERVAL = 1000L;

    private final TerminalChannelRegistry channelRegistry;

    private final TerminalRouteRegistry routeRegistry;

    private final CommandAckPublisher ackPublisher;

    private final CmCommandProperties commandProperties;

    private final ThreadPoolExecutor ackPublishExecutor;

    private final PositionEventAssembler eventAssembler;

    private final PositionReportDispatcher positionDispatcher;

    private final OnlineLeaseStore onlineLeaseStore;

    private final TelemetryMetrics telemetryMetrics;

    private final GatewayProperties gatewayProperties;

    private final TerminalRegistrationService registrationService;

    private final ThreadPoolExecutor registrationExecutor;

    private final AtomicInteger platformSerial = new AtomicInteger();

    private final AtomicLong positionCount = new AtomicLong();

    private long incarnation;

    private ChannelRoute route;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        incarnation = channelRegistry.nextIncarnation();
        log.debug("终端连接建立, channelId={}, incarnation={}",
                ctx.channel().id().asShortText(), incarnation);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] frame) {
        if (frame.length > 0 && frame[0] == PRIVATE_FRAME_PREFIX) {
            onPrivatePosition(ctx, frame);
            return;
        }
        Jt808Message message = Jt808Decoder.decode(frame);
        if (message.failed() || message.header() == null) {
            log.warn("JT808 帧解析失败, channelId={}, error={}",
                    ctx.channel().id().asShortText(), message.errorMessage());
            return;
        }
        String simNo = message.header().simNo();
        int serialNo = message.header().serialNo();
        switch (message.header().messageId()) {
            case MSG_REGISTRATION -> onRegistration(ctx, message);
            case MSG_AUTHENTICATION -> onAuthentication(ctx, message);
            case MSG_DEREGISTRATION -> {
                replyPlatformAck(ctx, simNo, serialNo, MSG_DEREGISTRATION);
                deregister();
            }
            case MSG_HEARTBEAT -> {
                replyPlatformAck(ctx, simNo, serialNo, MSG_HEARTBEAT);
                renewRoute(simNo);
                renewLease(simNo);
            }
            case MSG_GENERIC_ACK -> onGenericAck(message);
            case MSG_POSITION_QUERY_ACK -> onPositionQueryAck(message);
            case MSG_POSITION -> onPosition(ctx, message, frame);
            case MSG_BATCH_POSITION -> onBatchPosition(ctx, message, frame);
            default -> log.debug("暂不处理的上行消息, simNo={}, messageId={}",
                    simNo, String.format("0x%04X", message.header().messageId()));
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleEvent && idleEvent.state() == IdleState.READER_IDLE) {
            log.info("终端读空闲超时，关闭连接, channelId={}", ctx.channel().id().asShortText());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, event);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (route == null) {
            return;
        }
        boolean unbound = channelRegistry.unbind(route);
        if (unbound) {
            ChannelRoute closed = route;
            submitOffEventLoop(() -> onClosed(closed));
        } else {
            log.debug("旧连接关闭，路由已指向新连接，不删除, simNo={}, incarnation={}",
                    route.simNo(), route.incarnation());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("终端连接异常, channelId={}, cause={}", ctx.channel().id().asShortText(), cause.getMessage());
        ctx.close();
    }

    /**
     * 鉴权成功：本地连接表、Redis 路由与在线租约一起建立，并计入上线指标。
     */
    private void bind(ChannelHandlerContext ctx, String simNo) {
        ChannelRoute current = new ChannelRoute(simNo, commandProperties.getGatewayId(),
                ctx.channel().id().asLongText(), incarnation);
        channelRegistry.bind(current, ctx.channel());
        route = current;
        TerminalRoute redisRoute = new TerminalRoute(current.gatewayId(), current.incarnation());
        submitOffEventLoop(() -> {
            routeRegistry.register(simNo, redisRoute);
            onlineLeaseStore.register(simNo, current.gatewayId(), current.incarnation());
            telemetryMetrics.online();
            log.info("终端上线, simNo={}, gatewayId={}, incarnation={}",
                    simNo, current.gatewayId(), current.incarnation());
        });
    }

    /**
     * 心跳续租：路由 TTL 由心跳维持，掉线后自然过期，作为网关崩溃后的租约兜底。
     */
    private void renewRoute(String simNo) {
        if (route == null || !route.simNo().equals(simNo)) {
            return;
        }
        TerminalRoute redisRoute = new TerminalRoute(route.gatewayId(), route.incarnation());
        submitOffEventLoop(() -> routeRegistry.register(simNo, redisRoute));
    }

    /**
     * 在线租约续期。续租失败分两种：租约到期需要补登记，租约已被新连接持有时必须让位；
     * 用 setIfAbsent 区分，旧连接不会把在线状态改回自己的 incarnation。
     */
    private void renewLease(String simNo) {
        if (route == null || !route.simNo().equals(simNo)) {
            return;
        }
        ChannelRoute current = route;
        submitOffEventLoop(() -> {
            if (onlineLeaseStore.renew(current.simNo(), current.gatewayId(), current.incarnation())) {
                telemetryMetrics.leaseRenewed();
                return;
            }
            if (onlineLeaseStore.registerIfAbsent(current.simNo(), current.gatewayId(),
                    current.incarnation())) {
                log.info("在线租约已过期，心跳重新登记, simNo={}, incarnation={}",
                        current.simNo(), current.incarnation());
                return;
            }
            log.info("在线租约已由新连接接管，旧连接不再续租, simNo={}, incarnation={}",
                    current.simNo(), current.incarnation());
        });
    }

    /**
     * 断线清理。只有确认本地连接表中仍是这条连接时，才删除路由与在线租约。
     */
    private void onClosed(ChannelRoute closed) {
        TerminalRoute redisRoute = new TerminalRoute(closed.gatewayId(), closed.incarnation());
        routeRegistry.removeIfMatch(closed.simNo(), redisRoute);
        onlineLeaseStore.removeIfMatch(closed.simNo(), closed.gatewayId(), closed.incarnation());
        telemetryMetrics.offline();
        log.info("终端离线, simNo={}, incarnation={}", closed.simNo(), closed.incarnation());
    }

    /**
     * Redis 写入不能占用 EventLoop：鉴权与心跳是每连接最热的两条路径，
     * 一旦 Redis 抖动，同步调用会把该 EventLoop 上所有连接的读写一起拖住。
     */
    private void submitOffEventLoop(Runnable task) {
        try {
            ackPublishExecutor.execute(task);
        } catch (RejectedExecutionException ex) {
            log.error("网关业务线程池已满，任务未执行，Redis 路由与在线租约可能滞后于本地连接表", ex);
        }
    }

    private void replyPlatformAck(ChannelHandlerContext ctx, String simNo, int serialNo, int messageId) {
        ctx.writeAndFlush(Jt808Encoder.successAck(simNo, nextPlatformSerial(), serialNo, messageId));
    }

    private int nextPlatformSerial() {
        return platformSerial.incrementAndGet() & SERIAL_MASK;
    }

    /**
     * 注册判定要查库，不能占 EventLoop，也不与心跳续租、断线清理共用线程池：
     * 库慢时只应该堆注册，不应该把在线状态维护一起挤掉。
     * 结果码由 cm-domain 给出（0/2/4），鉴权码只在成功时下发。
     *
     * <p>池满时不回包而只告警：我们无法判定该终端是否合法，回一个假的「无该车辆」会让它永久放弃，
     * 不回包则终端按协议重试注册，等库缓过来自然接入。
     */
    private void onRegistration(ChannelHandlerContext ctx, Jt808Message message) {
        if (!(message.body() instanceof RegistrationRequest request)) {
            log.warn("注册报文缺少消息体, simNo={}", message.simNo());
            // 0x8100 的结果码里没有「消息有误」，畸形注册体只能用 0x8001 结果 2。
            writeAck(ctx, message, MSG_REGISTRATION, ACK_RESULT_MESSAGE_ERROR);
            return;
        }
        String simNo = message.simNo();
        int serialNo = message.header().serialNo();
        try {
            registrationExecutor.execute(() -> {
                RegistrationVerdict verdict = registrationService.evaluate(request.plateNo(),
                        request.plateColor(), simNo, request.terminalId());
                if (verdict == RegistrationVerdict.SUCCESS) {
                    telemetryMetrics.registrationAccepted();
                } else {
                    telemetryMetrics.registrationRejected();
                }
                String authCode = verdict == RegistrationVerdict.SUCCESS ? gatewayProperties.getAuthCode() : "";
                ctx.writeAndFlush(Jt808Encoder.registrationAck(simNo, nextPlatformSerial(), serialNo,
                        verdict.code(), authCode));
            });
        } catch (RejectedExecutionException ex) {
            log.error("注册判定队列已满，本次不回应答等终端重试, simNo={}", simNo, ex);
        }
    }

    /**
     * 鉴权：码不匹配就回失败且不建立路由，但不断开连接——断开会引发重连风暴；
     * 同一连接后续用正确码重试仍可绑定。鉴权码本身不进日志。
     */
    private void onAuthentication(ChannelHandlerContext ctx, Jt808Message message) {
        String configured = gatewayProperties.getAuthCode();
        String provided = message.body() instanceof AuthenticationRequest request ? request.registerNo() : null;
        boolean acceptable = configured != null && !configured.isEmpty() && configured.equals(provided);
        if (!acceptable) {
            telemetryMetrics.authRejected();
            log.warn("终端鉴权失败，不建立路由, simNo={}, providedBlank={}",
                    message.simNo(), provided == null || provided.isEmpty());
            writeAck(ctx, message, MSG_AUTHENTICATION, ACK_RESULT_FAILURE);
            return;
        }
        writeAck(ctx, message, MSG_AUTHENTICATION, ACK_RESULT_SUCCESS);
        bind(ctx, message.simNo());
    }

    /**
     * 注销：回包后按断线同一条路径清理，不另写一套。
     */
    private void deregister() {
        ChannelRoute closing = route;
        if (closing == null) {
            return;
        }
        route = null;
        if (channelRegistry.unbind(closing)) {
            submitOffEventLoop(() -> onClosed(closing));
            telemetryMetrics.deregistered();
        }
    }

    private void writeAck(ChannelHandlerContext ctx, Jt808Message message, int ackMessageId, int result) {
        ctx.writeAndFlush(Jt808Encoder.genericAck(message.simNo(), nextPlatformSerial(),
                message.header().serialNo(), ackMessageId, result));
    }

    private void onGenericAck(Jt808Message message) {
        if (!(message.body() instanceof GenericAck ack)) {
            log.warn("通用应答报文缺少消息体, simNo={}", message.simNo());
            return;
        }
        publishAck(message, ack.responseSerialNo(), MSG_GENERIC_ACK, ack.result());
    }

    private void onPositionQueryAck(Jt808Message message) {
        if (!(message.body() instanceof PositionQueryAck ack)) {
            log.warn("位置查询应答报文缺少消息体, simNo={}", message.simNo());
            return;
        }
        publishAck(message, ack.responseSerialNo(), MSG_POSITION_QUERY_ACK, ACK_RESULT_SUCCESS);
    }

    private void publishAck(Jt808Message message, int terminalSerialNo, int ackMessageId, int ackResult) {
        String simNo = message.simNo();
        long currentIncarnation = incarnation;
        String eventId = simNo + "-" + terminalSerialNo + "-" + currentIncarnation + "-" + ackMessageId;
        CommandAckEvent event = new CommandAckEvent(CommandAckEvent.SCHEMA_VERSION, eventId,
                CommandAckEvent.EVENT_TYPE, simNo, terminalSerialNo, currentIncarnation,
                commandProperties.getGatewayId(), ackMessageId, ackResult,
                truncate(message.frameHex()), LocalDateTime.now().toString());
        try {
            ackPublishExecutor.execute(() -> ackPublisher.publish(event));
        } catch (RejectedExecutionException ex) {
            log.error("终端应答投递队列已满，本次应答未上报, simNo={}, serialNo={}, ackMessageId={}",
                    simNo, terminalSerialNo, ackMessageId);
        }
    }

    private void onPosition(ChannelHandlerContext ctx, Jt808Message message, byte[] frame) {
        if (!(message.body() instanceof PositionReport report)) {
            log.warn("定位报文缺少消息体, simNo={}", message.simNo());
            return;
        }
        long total = positionCount.incrementAndGet();
        if (total % LOG_INTERVAL == 0) {
            log.info("定位报文计数, simNo={}, total={}", message.simNo(), total);
        }
        PositionEvent event = eventAssembler.assemble(message.simNo(), MSG_POSITION,
                message.header().serialNo(), report, PositionEvent.BRANCH_STANDARD, 0, frame, Instant.now());
        positionDispatcher.dispatch(ctx.channel(), message.simNo(), MSG_POSITION,
                message.header().serialNo(), List.of(event), true, platformSerial::incrementAndGet);
    }

    private void onBatchPosition(ChannelHandlerContext ctx, Jt808Message message, byte[] frame) {
        if (!(message.body() instanceof BatchPositionReport batch)) {
            log.warn("批量定位报文缺少消息体, simNo={}", message.simNo());
            return;
        }
        if (batch.positions().isEmpty()) {
            log.warn("批量定位条数为 0，直接应答受理, simNo={}", message.simNo());
            replyPlatformAck(ctx, message.simNo(), message.header().serialNo(), MSG_BATCH_POSITION);
            return;
        }
        Instant ingestTime = Instant.now();
        List<PositionEvent> events = new ArrayList<>(batch.positions().size());
        for (int index = 0; index < batch.positions().size(); index++) {
            events.add(eventAssembler.assemble(message.simNo(), MSG_BATCH_POSITION,
                    message.header().serialNo(), batch.positions().get(index), PositionEvent.BRANCH_BATCH,
                    index, frame, ingestTime));
        }
        log.info("批量定位待发布, simNo={}, count={}", message.simNo(), events.size());
        positionDispatcher.dispatch(ctx.channel(), message.simNo(), MSG_BATCH_POSITION,
                message.header().serialNo(), events, true, platformSerial::incrementAndGet);
    }

    /**
     * 私有 $ 帧没有标准消息头与校验码，按固定长度解析；落库但不回应答。
     */
    private void onPrivatePosition(ChannelHandlerContext ctx, byte[] frame) {
        Jt808Message message = Jt808Decoder.decodePrivate(frame);
        if (message.failed() || message.header() == null || !(message.body() instanceof PositionReport report)) {
            log.warn("私有定位帧解析失败, channelId={}, error={}",
                    ctx.channel().id().asShortText(), message.errorMessage());
            return;
        }
        PositionEvent event = eventAssembler.assemble(message.simNo(), MSG_POSITION,
                message.header().serialNo(), report, PositionEvent.BRANCH_PRIVATE, 0, frame, Instant.now());
        positionDispatcher.dispatch(ctx.channel(), message.simNo(), MSG_POSITION,
                message.header().serialNo(), List.of(event), false, platformSerial::incrementAndGet);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= RAW_HEX_MAX_LENGTH ? value : value.substring(0, RAW_HEX_MAX_LENGTH);
    }
}
