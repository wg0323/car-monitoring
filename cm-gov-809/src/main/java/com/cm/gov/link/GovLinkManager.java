package com.cm.gov.link;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.gov.config.GovSuperiorProperties;
import com.cm.gov.handler.Jt809SuperiorHandler;
import com.cm.infrastructure.gov809.Gov809LinkService;
import com.cm.infrastructure.gov809.Jt809CommandLogService;
import com.cm.infrastructure.persistence.entity.CmPlatformInfo;
import com.cm.infrastructure.persistence.mapper.CmPlatformInfoMapper;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import com.cm.protocol.jt809.body.SubLinkLogin;
import com.cm.transport.Jt809BytesEncoder;
import com.cm.transport.Jt809FrameDecoder;
import com.cm.transport.Jt809LinkSession;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 监管侧下级平台链路管理。每个接入码一条链路，主链路方向是下级连入，
 * 从链路方向由本端回连下级声明的地址并发 0x9001。状态与检查点按平台写 cm_gov809_link。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovLinkManager {

    public static final AttributeKey<Long> PLATFORM_ID = AttributeKey.valueOf("cm.gov809.platformId");

    private final GovSuperiorProperties properties;

    private final Gov809LinkService linkService;

    private final Jt809CommandLogService commandLogService;

    private final CmPlatformInfoMapper platformInfoMapper;

    private final MeterRegistry meterRegistry;

    private final EventLoopGroup eventLoopGroup;

    private final ThreadPoolExecutor businessExecutor;

    private final Map<Long, Jt809LinkSession> sessions = new ConcurrentHashMap<>();

    private final Map<Long, ScheduledFuture<?>> subHeartbeats = new ConcurrentHashMap<>();

    public Jt809LinkSession session(long platformId) {
        return sessions.computeIfAbsent(platformId, Jt809LinkSession::new);
    }

    public void bindInbound(long platformId, Channel channel) {
        session(platformId).bindMain(channel);
        linkService.markMainLink(platformId, Gov809LinkService.ROLE_GOV_DOWNSTREAM,
                Gov809LinkService.STATE_CONNECTED, "");
    }

    public void onMainLoggedIn(long platformId) {
        session(platformId).markMainLoggedIn(true);
        linkService.markMainLink(platformId, Gov809LinkService.ROLE_GOV_DOWNSTREAM,
                Gov809LinkService.STATE_LOGGED_IN, "");
        log.info("809 下级平台主链路登录成功, platformId={}", platformId);
    }

    public void onInboundClosed(long platformId, Channel channel) {
        Jt809LinkSession session = session(platformId);
        session.unbind(channel);
        if (!session.isMainActive()) {
            linkService.markMainLink(platformId, Gov809LinkService.ROLE_GOV_DOWNSTREAM,
                    Gov809LinkService.STATE_DISCONNECTED, "主链路断开");
            log.warn("809 下级平台主链路断开, platformId={}", platformId);
        }
    }

    public void onSubLoggedIn(long platformId) {
        session(platformId).markSubLoggedIn(true);
        linkService.markSubLink(platformId, Gov809LinkService.ROLE_GOV_DOWNSTREAM,
                Gov809LinkService.STATE_LOGGED_IN, "");
        log.info("809 下级平台从链路连接成功, platformId={}", platformId);
    }

    public void onSubClosed(long platformId, Channel channel) {
        Jt809LinkSession session = session(platformId);
        session.unbind(channel);
        if (!session.isSubActive()) {
            cancelSubHeartbeat(platformId);
            linkService.markSubLink(platformId, Gov809LinkService.ROLE_GOV_DOWNSTREAM,
                    Gov809LinkService.STATE_DISCONNECTED, "从链路断开");
            log.warn("809 下级平台从链路断开, platformId={}", platformId);
        }
    }

    /**
     * 按 0x1001 里声明的从链路地址回连并发 0x9001，连不上就只回报失败，不阻塞主链路。
     */
    public boolean connectSubLink(long platformId, String host, int port) {
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getSubLinkConnectTimeoutMillis())
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.attr(PLATFORM_ID).set(platformId);
                        socketChannel.pipeline()
                                .addLast(new IdleStateHandler(properties.getSubLinkIdleTimeoutSeconds(), 0, 0))
                                .addLast(new Jt809FrameDecoder())
                                .addLast(new Jt809BytesEncoder())
                                .addLast(new Jt809SuperiorHandler(platformId, GovLinkManager.this,
                                        commandLogService, meterRegistry, properties, businessExecutor));
                    }
                });
        try {
            Channel channel = bootstrap.connect(host, port).sync().channel();
            session(platformId).bindSub(channel);
            linkService.markSubLink(platformId, Gov809LinkService.ROLE_GOV_DOWNSTREAM,
                    Gov809LinkService.STATE_CONNECTED, "");
            channel.writeAndFlush(session(platformId).encode(Jt809Constants.MSG_SUB_LINK_LOGIN,
                    new SubLinkLogin(platformId).encode()));
            scheduleSubHeartbeat(platformId, channel);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            log.warn("809 回连下级从链路失败, platformId={}, {}:{}", platformId, host, port, ex);
            return false;
        }
    }

    public boolean sendToLower(long platformId, int messageId, byte[] body) {
        Jt809LinkSession session = sessions.get(platformId);
        if (session == null || !session.hasUsableLink()) {
            return false;
        }
        return session.writeAny(session.encode(messageId, body), properties.getWriteTimeoutMillis());
    }

    public MainLinkLoginAck validate(long platformId, long userId, String password) {
        CmPlatformInfo info = platformInfoMapper.selectOne(Wrappers.<CmPlatformInfo>lambdaQuery()
                .eq(CmPlatformInfo::getPlatformId, platformId)
                .last("LIMIT 1"));
        if (info == null || !Integer.valueOf(1).equals(info.getUpstream())) {
            return new MainLinkLoginAck(MainLinkLoginAck.RESULT_CENTER_INVALID, platformId);
        }
        if (!String.valueOf(userId).equals(info.getUserId())) {
            return new MainLinkLoginAck(MainLinkLoginAck.RESULT_USER_NOT_REGISTERED, platformId);
        }
        if (!password.equals(info.getPassword())) {
            return new MainLinkLoginAck(MainLinkLoginAck.RESULT_PASSWORD_INVALID, platformId);
        }
        return new MainLinkLoginAck(MainLinkLoginAck.RESULT_SUCCESS, platformId);
    }

    private void scheduleSubHeartbeat(long platformId, Channel channel) {
        cancelSubHeartbeat(platformId);
        ScheduledFuture<?> task = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(session(platformId)
                        .encode(Jt809Constants.MSG_SUB_LINK_TEST, new byte[0]));
            }
        }, properties.getHeartbeatIntervalSeconds(), properties.getHeartbeatIntervalSeconds(),
                TimeUnit.SECONDS);
        subHeartbeats.put(platformId, task);
    }

    /**
     * 同一接入码重登时先停旧心跳：旧连接已被 bindSub 关闭，
     * 但任务不取消就会在 EventLoop 定时队列里长期空转。
     */
    private void cancelSubHeartbeat(long platformId) {
        ScheduledFuture<?> task = subHeartbeats.remove(platformId);
        if (task != null) {
            task.cancel(false);
        }
    }
}
 
