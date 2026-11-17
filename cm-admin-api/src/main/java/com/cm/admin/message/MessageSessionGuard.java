package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * 聊天连接保活与会话回收：会话失效、心跳超时、账号被禁用三种情况主动断开。
 * 判据与实时监控一致——权限码是登录时快照，账号禁用只能靠会话复核兜住。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSessionGuard {

    private static final String DISABLED_STATE = "disabled";

    private final MessageSessionRegistry registry;

    private final SessionRepository<? extends Session> sessionRepository;

    private final CmUserMapper userMapper;

    private final MessageProperties properties;

    /**
     * 自己建调度器，不注册成容器 bean：容器里只能有一个 TaskScheduler，
     * 占用它会把 Outbox relay 与超时清扫排到同一条单线程后面。
     */
    private final ThreadPoolTaskScheduler scheduler = createScheduler();

    private ScheduledFuture<?> task;

    @PostConstruct
    void start() {
        scheduler.initialize();
        long interval = Math.max(1_000L, properties.getCheckIntervalMillis());
        task = scheduler.scheduleWithFixedDelay(this::reap, Duration.ofMillis(interval));
    }

    @PreDestroy
    void stop() {
        if (task != null) {
            task.cancel(false);
        }
        scheduler.shutdown();
    }

    private static ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("cm-message-session-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        return taskScheduler;
    }

    /** 包可见：便于单测直接跑一轮复核，不必等调度线程。 */
    void reap() {
        for (MessageSessionRegistry.MessageConnection connection : registry.connections()) {
            try {
                if (!sessionAlive(connection.httpSessionId())) {
                    closeAndLog(connection, "会话已失效");
                    continue;
                }
                if (heartbeatTimedOut(connection)) {
                    closeAndLog(connection, "心跳超时");
                    continue;
                }
                if (!accountActive(connection.userId())) {
                    closeAndLog(connection, "账号已禁用或不存在");
                }
            } catch (RuntimeException ex) {
                // Redis 或数据库抖动不该把一批好连接全踢掉，本轮跳过、下一轮再看。
                log.warn("消息中心连接复核失败, sessionId={}", connection.sessionId(), ex);
            }
        }
    }

    private boolean sessionAlive(String httpSessionId) {
        return !httpSessionId.isBlank() && sessionRepository.findById(httpSessionId) != null;
    }

    private boolean heartbeatTimedOut(MessageSessionRegistry.MessageConnection connection) {
        long timeout = properties.getHeartbeatTimeoutMillis();
        return timeout > 0 && System.currentTimeMillis() - connection.lastSeenMillis() > timeout;
    }

    private boolean accountActive(Long userId) {
        CmUser user = userMapper.selectById(userId);
        return user != null && !DISABLED_STATE.equalsIgnoreCase(user.getUserState());
    }

    private void closeAndLog(MessageSessionRegistry.MessageConnection connection, String reason) {
        log.info("主动断开消息中心连接, sessionId={}, userId={}, reason={}", connection.sessionId(),
                connection.userId(), reason);
        // 先告知原因再断：否则页面只会看到一次莫名断线，然后盲目重连。
        registry.sendFrame(connection.sessionId(),
                MessageFrames.write(MessageFrames.error(MessageFrames.CODE_UNAUTHORIZED, reason)));
        registry.close(connection.sessionId(), new CloseStatus(MessageFrames.CODE_UNAUTHORIZED, reason), reason);
    }
}
