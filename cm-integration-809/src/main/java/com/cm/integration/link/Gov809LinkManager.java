package com.cm.integration.link;

import com.cm.infrastructure.gov809.Gov809LinkService;
import com.cm.integration.config.Gov809Properties;
import com.cm.transport.Jt809LinkSession;
import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 企业侧 809 链路状态。主链路（本端连出）与从链路（上级连入）分开登记：
 * 只有两边都断开才算离线；状态变更立即落库，并同步 platformstate 快照给 P4 图标用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Gov809LinkManager {

    private final Gov809Properties properties;

    private final Gov809LinkService linkService;

    private final ApplicationEventPublisher eventPublisher;

    private Jt809LinkSession session;

    @PostConstruct
    void init() {
        session = new Jt809LinkSession(properties.getPlatformId());
    }

    public Jt809LinkSession session() {
        return session;
    }

    public void onMainConnected(Channel channel) {
        session.bindMain(channel);
        linkService.markMainLink(properties.getPlatformId(),
                Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM, Gov809LinkService.STATE_CONNECTED, "");
        log.info("809 主链路 TCP 已建立, remote={}", channel.remoteAddress());
    }

    public void onMainLoginSuccess() {
        session.markMainLoggedIn(true);
        linkService.markMainLink(properties.getPlatformId(),
                Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM, Gov809LinkService.STATE_LOGGED_IN, "");
        log.info("809 主链路登录成功, platformId={}", properties.getPlatformId());
        eventPublisher.publishEvent(new Gov809LinkUpEvent(properties.getPlatformId()));
    }

    public void onMainClosed(Channel channel) {
        session.unbind(channel);
        if (!session.isMainActive()) {
            linkService.markMainLink(properties.getPlatformId(),
                    Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM, Gov809LinkService.STATE_DISCONNECTED,
                    "主链路断开");
            log.warn("809 主链路已断开");
        }
    }

    public void onSubConnected(Channel channel) {
        session.bindSub(channel);
        linkService.markSubLink(properties.getPlatformId(),
                Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM, Gov809LinkService.STATE_CONNECTED, "");
    }

    public void onSubLoginSuccess() {
        session.markSubLoggedIn(true);
        linkService.markSubLink(properties.getPlatformId(),
                Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM, Gov809LinkService.STATE_LOGGED_IN, "");
        log.info("809 从链路连接成功, platformId={}", properties.getPlatformId());
        eventPublisher.publishEvent(new Gov809LinkUpEvent(properties.getPlatformId()));
    }

    public void onSubClosed(Channel channel) {
        session.unbind(channel);
        if (!session.isSubActive()) {
            linkService.markSubLink(properties.getPlatformId(),
                    Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM, Gov809LinkService.STATE_DISCONNECTED,
                    "从链路断开");
            log.warn("809 从链路已断开");
        }
    }

    /**
     * 发业务报文：链路不可用时返回 false，由调用方写入积压队列，不在这里丢数据。
     */
    public boolean send(int messageId, byte[] body) {
        if (session == null || !session.hasUsableLink()) {
            return false;
        }
        byte[] frame = session.encode(messageId, body);
        return session.writeAny(frame, properties.getWriteTimeoutMillis());
    }

    /**
     * 只从从链路发送。0x1007 / 0x1008 是「上级平台侧」通知，
     * 旧实现同样走 SendFromSubLink，不能降级到主链路。
     */
    public boolean sendFromSubLink(int messageId, byte[] body) {
        if (session == null || !session.isSubLoggedIn()) {
            return false;
        }
        byte[] frame = session.encode(messageId, body);
        return session.writeSub(frame, properties.getWriteTimeoutMillis());
    }
}
 
