package com.cm.gateway.server;

import com.cm.gateway.downlink.CommandAckPublisher;
import com.cm.gateway.downlink.TerminalChannelRegistry;
import com.cm.gateway.telemetry.PositionEventAssembler;
import com.cm.gateway.telemetry.PositionReportDispatcher;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.TerminalRouteRegistry;
import com.cm.infrastructure.service.TerminalRegistrationService;
import com.cm.infrastructure.telemetry.OnlineLeaseStore;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import com.cm.transport.Jt808BytesEncoder;
import com.cm.transport.Jt808FrameDecoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * JT808 连接装配：空闲检测 → 切帧 → 下行字节编码 → 业务分发。
 * Handler 持有连接级状态，必须每个 Channel 新建一个，不能做成单例共享。
 * 下行编码器不可或缺：业务侧只写 byte[]，没有它 NioSocketChannel 会拒绝写入。
 */
@Component
@RequiredArgsConstructor
public class Jt808ChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int READER_IDLE_SECONDS = 180;

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

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline()
                .addLast(new IdleStateHandler(READER_IDLE_SECONDS, 0, 0))
                .addLast(new Jt808FrameDecoder())
                .addLast(new Jt808BytesEncoder())
                .addLast(new Jt808InboundHandler(channelRegistry, routeRegistry, ackPublisher,
                        commandProperties, ackPublishExecutor, eventAssembler, positionDispatcher,
                        onlineLeaseStore, telemetryMetrics, gatewayProperties, registrationService,
                        registrationExecutor));
    }
}
