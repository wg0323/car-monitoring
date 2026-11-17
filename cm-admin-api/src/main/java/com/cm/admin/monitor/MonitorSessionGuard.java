package com.cm.admin.monitor;

import com.cm.admin.config.MonitorProperties;
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
 * 连接保活与权限回收（14.2）。三件事按不同节奏做：
 * 会话是否还在、心跳是否还活着、账号是否仍启用。任一不满足就主动断开，不等下一次推送失败。
 *
 * <p>权限码快照随会话走（P4-1 已裁决），所以权限回收的生效方式是让会话失效；
 * 这里不与快照口径打架，只补「账号被禁用」这一条会话本身发现不了的路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorSessionGuard {

    private static final String DISABLED_STATE = "disabled";

    private final MonitorSessionRegistry registry;

    private final SessionRepository<? extends Session> sessionRepository;

    private final CmUserMapper userMapper;

    /**
     * 自己建调度器，不注册成容器 bean：容器里只能有一个 TaskScheduler，
     * 让监控复核去占用它会把 P3 的 Outbox relay 与超时清扫排到同一条单线程后面。
     */
    private final ThreadPoolTaskScheduler scheduler = createScheduler();

    private final MonitorProperties properties;

    private ScheduledFuture<?> task;

    @PostConstruct
    void start() {
        scheduler.initialize();
        long interval = Math.max(100L, properties.getSession().getCheckIntervalMillis());
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
        taskScheduler.setThreadNamePrefix("cm-monitor-session-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        return taskScheduler;
    }

    /** 包可见：便于单测直接跑一轮复核，不必等调度线程。 */
    void reap() {
        for (MonitorSessionRegistry.MonitorConnection connection : registry.connections()) {
            try {
                if (!sessionAlive(connection.httpSessionId())) {
                    closeAndLog(connection, "会话已失效");
                    continue;
                }
                if (heartbeatTimedOut(connection)) {
                    closeAndLog(connection, "心跳超时");
                    continue;
                }
                if (accountCheckDue(connection)) {
                    if (!accountActive(connection.userId())) {
                        closeAndLog(connection, "账号已禁用或不存在");
                        continue;
                    }
                    registry.touchAccountCheck(connection.sessionId());
                }
            } catch (RuntimeException ex) {
                // Redis 或数据库抖动不该把一批好连接全踢掉，本轮跳过、下一轮再看。
                log.warn("实时监控连接复核失败, sessionId={}", connection.sessionId(), ex);
            }
        }
    }

    private boolean sessionAlive(String httpSessionId) {
        return !httpSessionId.isBlank() && sessionRepository.findById(httpSessionId) != null;
    }

    private boolean heartbeatTimedOut(MonitorSessionRegistry.MonitorConnection connection) {
        long timeout = properties.getSession().getHeartbeatTimeoutMillis();
        return timeout > 0 && System.currentTimeMillis() - connection.lastSeenMillis() > timeout;
    }

    private boolean accountCheckDue(MonitorSessionRegistry.MonitorConnection connection) {
        long interval = properties.getSession().getAccountCheckIntervalMillis();
        return interval > 0 && System.currentTimeMillis() - connection.lastAccountCheckMillis() >= interval;
    }

    private boolean accountActive(Long userId) {
        CmUser user = userMapper.selectById(userId);
        return user != null && !DISABLED_STATE.equalsIgnoreCase(user.getUserState());
    }

    private void closeAndLog(MonitorSessionRegistry.MonitorConnection connection, String reason) {
        log.info("主动断开实时监控连接, sessionId={}, userId={}, reason={}", connection.sessionId(),
                connection.userId(), reason);
        // 先告知原因再断：否则页面只会看到一次莫名断线，然后盲目重连。
        registry.sendFrame(connection.sessionId(),
                MonitorFrames.write(MonitorFrames.error(MonitorFrames.CODE_UNAUTHORIZED, reason)));
        registry.close(connection.sessionId(), new CloseStatus(MonitorFrames.CODE_UNAUTHORIZED, reason), reason);
    }
}
