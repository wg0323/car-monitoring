package com.cm.gateway.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * JT808 TCP 监听。
 * EventLoop 只做网络、编解码与轻量分发；端口 <= 0 时不监听，便于只跑 HTTP 探针的调试场景。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Jt808Server {

    private static final int BACKLOG = 128;

    private static final int LOW_WATER_MARK = 32 * 1024;

    private static final int HIGH_WATER_MARK = 64 * 1024;

    private final GatewayProperties properties;

    private final Jt808ChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private Channel serverChannel;

    @PostConstruct
    public void start() {
        if (properties.getTcpPort() <= 0) {
            log.info("cm.gateway.tcp-port <= 0，跳过 JT808 TCP 监听");
            return;
        }
        requireAuthCode();
        // Netty 4.2 已弃用 NioEventLoopGroup，改用 MultiThread + NioIoHandler 的写法。
        bossGroup = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("cm-jt808-boss"),
                NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(0, new DefaultThreadFactory("cm-jt808-io"),
                NioIoHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, BACKLOG)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK))
                .childHandler(channelInitializer);
        try {
            serverChannel = bootstrap.bind(properties.getTcpPort()).sync().channel();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JT808 监听启动被中断", ex);
        }
        log.info("JT808 监听已启动, port={}", port());
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        log.info("JT808 监听已停止");
    }

    /**
     * 鉴权码是接入凭据，空值必须启动即失败：否则注册会回「成功」但不下发码，
     * 终端之后的每次鉴权都会被拒，全网静默无法上线，排查方向被那句「注册成功」带偏。
     */
    private void requireAuthCode() {
        String authCode = properties.getAuthCode();
        if (authCode == null || authCode.isBlank()) {
            throw new IllegalStateException(
                    "cm.gateway.auth-code 未配置，拒绝启动：需通过 CM_GATEWAY_AUTH_CODE 注入非空鉴权码");
        }
    }

    /**
     * 实际绑定端口；配置为 0 时由系统分配，测试据此获取随机端口。
     */
    public int port() {
        if (serverChannel == null) {
            return -1;
        }
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }
}
