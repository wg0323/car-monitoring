package com.cm.integration.link;

import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.gov809.WarnMsgUrgeTodoService;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import com.cm.integration.config.Gov809Properties;
import com.cm.transport.Jt809BytesEncoder;
import com.cm.transport.Jt809FrameDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 企业侧从链路监听（12345）。上级平台登录成功后会到这个端口发 0x9001，
 * 本端回 0x9002；断线只影响从链路，主链路仍可收发，所以两侧状态分开记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Jt809SubLinkServer implements SmartLifecycle {

    private static final int BACKLOG = 16;

    private final EventLoopGroup eventLoopGroup;

    private final Gov809Properties properties;

    private final Gov809LinkManager linkManager;

    private final WarnMsgUrgeTodoService warnTodoService;

    private final Jt809CommandLogService commandLogService;

    private final CmVehicleMapper vehicleMapper;

    private final ThreadPoolExecutor businessExecutor;

    private volatile Channel serverChannel;

    private volatile boolean running;

    @Override
    public void start() {
        if (!properties.isEnabled() || properties.getSubLinkPort() <= 0) {
            log.info("cm.gov809.enabled=false 或 sub-link-port<=0，跳从链路监听");
            return;
        }
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, BACKLOG)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline()
                                .addLast(new IdleStateHandler(properties.getIdleTimeoutSeconds(), 0, 0))
                                .addLast(new Jt809FrameDecoder())
                                .addLast(new Jt809BytesEncoder())
                                .addLast(new Jt809InboundHandler(linkManager, warnTodoService,
                                        commandLogService, vehicleMapper, properties, businessExecutor,
                                        false));
                        businessExecutor.execute(() -> linkManager.onSubConnected(socketChannel));
                    }
                });
        try {
            serverChannel = bootstrap.bind(new InetSocketAddress(properties.getSubLinkBindAddress(),
                    properties.getSubLinkPort())).sync().channel();
            running = true;
            log.info("809 从链路监听已启动, port={}", properties.getSubLinkPort());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("809 从链路监听启动被中断", ex);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        log.info("809 从链路监听已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
 
