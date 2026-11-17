package com.cm.gov.server;

import com.cm.gov.config.GovSuperiorProperties;
import com.cm.gov.handler.Jt809SuperiorHandler;
import com.cm.gov.link.GovLinkManager;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.transport.Jt809BytesEncoder;
import com.cm.transport.Jt809FrameDecoder;
import io.micrometer.core.instrument.MeterRegistry;
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
 * 监管侧 19100 监听。新连接只登记为 TCP 已连，收到有效 0x1001 后才算在线，
 * 避免旧实现把“连上了”当成“登录了”导致主链路图标误报。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Jt809SuperiorServer implements SmartLifecycle {

    private static final int BACKLOG = 32;

    private final EventLoopGroup eventLoopGroup;

    private final GovSuperiorProperties properties;

    private final GovLinkManager linkManager;

    private final Jt809CommandLogService commandLogService;

    private final MeterRegistry meterRegistry;

    private final ThreadPoolExecutor businessExecutor;

    private volatile Channel serverChannel;

    private volatile boolean running;

    @Override
    public void start() {
        if (!properties.isEnabled() || properties.getListenPort() <= 0) {
            log.info("cm.gov809.server.enabled=false 或 listen-port<=0，跳过 19100 监听");
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
                                .addLast(new Jt809SuperiorHandler(null, linkManager, commandLogService,
                                        meterRegistry, properties, businessExecutor));
                    }
                });
        try {
            serverChannel = bootstrap.bind(new InetSocketAddress(properties.getListenAddress(),
                    properties.getListenPort())).sync().channel();
            running = true;
            log.info("809 监管侧监听已启动, port={}", properties.getListenPort());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("809 监管侧监听启动被中断", ex);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        log.info("809 监管侧监听已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
 
