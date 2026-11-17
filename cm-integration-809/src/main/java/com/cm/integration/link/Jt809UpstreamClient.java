package com.cm.integration.link;

import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.gov809.WarnMsgUrgeTodoService;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import com.cm.integration.config.Gov809Properties;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.body.MainLinkLogin;
import com.cm.transport.Jt809BytesEncoder;
import com.cm.transport.Jt809FrameDecoder;
import com.cm.transport.Jt809LinkSession;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 上级平台 Netty Client。重连在独立线程里做指数退避加抖动，不用 EventLoop：
 * 连不上时反复重试不应该阻塞网络线程，旧实现每次重连都新建线程池，断链风暴会持续涨线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Jt809UpstreamClient implements SmartLifecycle {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    private static final long BACKOFF_MULTIPLIER = 2L;

    private static final double JITTER_RATIO = 0.2D;

    /** 暂停期间与唤醒退避的检查粒度，取 1 秒足够应对人工操作。 */
    private static final long PAUSE_CHECK_MILLIS = 1_000L;

    private final EventLoopGroup eventLoopGroup;

    private final Gov809Properties properties;

    private final Gov809LinkManager linkManager;

    private final WarnMsgUrgeTodoService warnTodoService;

    private final Jt809CommandLogService commandLogService;

    private final CmVehicleMapper vehicleMapper;

    private final ThreadPoolExecutor businessExecutor;

    private ExecutorService connector;

    private volatile boolean running;

    private volatile Channel channel;

    private volatile ScheduledFuture<?> heartbeatTask;

    /** 0x1003 注销后置位：不再自动重连，直到 0x1001 或进程重启。 */
    private volatile boolean paused;

    private final Object pauseLock = new Object();

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("cm.gov809.enabled=false，跳过上级平台连接");
            return;
        }
        running = true;
        connector = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "cm-jt809-connector");
                    thread.setDaemon(true);
                    return thread;
                });
        connector.execute(this::connectLoop);
    }

    @Override
    public void stop() {
        running = false;
        cancelHeartbeat();
        Channel current = channel;
        if (current != null) {
            current.close().awaitUninterruptibly(5_000);
        }
        if (connector != null) {
            connector.shutdownNow();
        }
        log.info("809 上级平台客户端已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void connectLoop() {
        long backoff = properties.getReconnectInitialMillis();
        while (running) {
            awaitResumed();
            if (!running) {
                return;
            }
            try {
                Channel current = connect();
                channel = current;
                linkManager.onMainConnected(current);
                current.writeAndFlush(linkManager.session().encode(Jt809Constants.MSG_MAIN_LINK_LOGIN,
                        loginBody()));
                scheduleHeartbeat(current);
                backoff = properties.getReconnectInitialMillis();
                current.closeFuture().sync();
                cancelHeartbeat();
                log.warn("809 主链路连接已关闭, 准备重连");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                // sync() 在连接被拒时抛出的是 IOException 子类（AnnotatedConnectException），
                // 只捕 RuntimeException 会让 connector 线程退出，之后 0x1001 也无法恢复链路。
                log.warn("809 主链路连接失败, {}:{}, reason={}", properties.getUpstreamHost(),
                        properties.getUpstreamPort(), ex.toString());
            }
            if (!running) {
                return;
            }
            sleepWithJitter(backoff);
            backoff = Math.min(backoff * BACKOFF_MULTIPLIER, properties.getReconnectMaxMillis());
        }
    }

    /**
     * 暂停期间不建链，也不在断链后退避重试；由 0x1001 恢复。
     */
    private void awaitResumed() {
        synchronized (pauseLock) {
            while (running && paused) {
                try {
                    pauseLock.wait(PAUSE_CHECK_MILLIS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * 连接主链路（0x1001）。已登录时重发登录帧；未连接时恢复自动重连，
     * 实际建链与登录由 connector 完成，结果以链路状态与 RECEIVE 日志为准。
     */
    public boolean requestMainLink() {
        Jt809LinkSession session = linkManager.session();
        if (session.isMainLoggedIn()) {
            return session.writeMain(session.encode(Jt809Constants.MSG_MAIN_LINK_LOGIN, loginBody()),
                    properties.getWriteTimeoutMillis());
        }
        resume();
        return true;
    }

    /**
     * 主链路注销（0x1003）。发送成功后暂停自动重连，由 0x1001 或重启恢复，
     * 与旧系统「断开后需手动连接」的运维口径一致。
     */
    public boolean requestLogout() {
        Jt809LinkSession session = linkManager.session();
        boolean sent = session.writeMain(session.encode(Jt809Constants.MSG_MAIN_LINK_LOGOUT, new byte[0]),
                properties.getWriteTimeoutMillis());
        if (sent) {
            pause();
        }
        return sent;
    }

    public boolean isPaused() {
        return paused;
    }

    private void pause() {
        paused = true;
        log.info("809 主链路已暂停自动重连，等待 0x1001 恢复");
    }

    private void resume() {
        synchronized (pauseLock) {
            if (!paused) {
                return;
            }
            paused = false;
            pauseLock.notifyAll();
        }
        log.info("809 主链路已恢复自动重连");
    }

    private Channel connect() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.pipeline()
                                .addLast(new IdleStateHandler(properties.getIdleTimeoutSeconds(), 0, 0))
                                .addLast(new Jt809FrameDecoder())
                                .addLast(new Jt809BytesEncoder())
                                .addLast(new Jt809InboundHandler(linkManager, warnTodoService,
                                        commandLogService, vehicleMapper, properties, businessExecutor,
                                        true));
                    }
                });
        return bootstrap.connect(properties.getUpstreamHost(), properties.getUpstreamPort())
                .sync().channel();
    }

    private byte[] loginBody() {
        return new MainLinkLogin(properties.getLoginUserId(), properties.getLoginPassword(),
                properties.getSubLinkAdvertiseIp(), properties.getSubLinkPort()).encode();
    }

    /**
     * 主链路心跳 0x1005，间隔与旧实现一致（20 秒）。
     * 每次重连必须先把上一个任务取消，否则共享 EventLoop 的定时队列会随重连次数持续增长。
     */
    private void scheduleHeartbeat(Channel current) {
        cancelHeartbeat();
        heartbeatTask = current.eventLoop().scheduleAtFixedRate(() -> {
            if (current.isActive() && linkManager.session().hasUsableLink()) {
                current.writeAndFlush(linkManager.session()
                        .encode(Jt809Constants.MSG_MAIN_LINK_TEST, new byte[0]));
            }
        }, properties.getHeartbeatIntervalSeconds(), properties.getHeartbeatIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
            heartbeatTask = null;
        }
    }

    private void sleepWithJitter(long backoff) {
        long jitter = (long) (backoff * JITTER_RATIO * ThreadLocalRandom.current().nextDouble());
        synchronized (pauseLock) {
            try {
                // 用 wait 而不是 sleep：0x1001 恢复时要能立刻唤醒，不必等完整个退避窗口。
                pauseLock.wait(backoff + jitter);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
}
 
