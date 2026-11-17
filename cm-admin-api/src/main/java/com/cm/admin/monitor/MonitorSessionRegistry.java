package com.cm.admin.monitor;

import com.cm.infrastructure.telemetry.PositionBroadcast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时监控订阅表。一个连接可以订阅多辆车，一辆车也可能被多个连接订阅：
 * 广播只按 SIM 找连接，车型与车牌由订阅时解析好的 vehicleId 带回去，推送路径上不查库。
 */
@Slf4j
@Component
public class MonitorSessionRegistry {

    /** sessionId → 连接。用于会话复核与心跳踢除。 */
    private final Map<String, MonitorConnection> connections = new ConcurrentHashMap<>();

    /** simNo → (sessionId → 连接)。广播按这个索引分发，避免每来一条位置就遍历全部连接。 */
    private final Map<String, Map<String, MonitorConnection>> bySimNo = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, Long userId, String httpSessionId) {
        MonitorConnection connection = new MonitorConnection(session, userId, httpSessionId);
        connections.put(session.getId(), connection);
        log.debug("实时监控连接建立, sessionId={}, userId={}, 在线连接数={}", session.getId(), userId,
                connections.size());
    }

    public void unregister(String sessionId) {
        MonitorConnection connection = connections.remove(sessionId);
        if (connection == null) {
            return;
        }
        for (String simNo : connection.subscribedSimNos()) {
            removeFromIndex(simNo, sessionId);
        }
        log.debug("实时监控连接关闭, sessionId={}, 在线连接数={}", sessionId, connections.size());
    }

    /**
     * 订阅。返回 false 表示超过单连接上限，调用方需要回错误帧而不是静默忽略。
     */
    public boolean subscribe(String sessionId, Long vehicleId, String simNo, int maxPerSession) {
        MonitorConnection connection = connections.get(sessionId);
        if (connection == null) {
            return false;
        }
        if (connection.subscribedCount() >= maxPerSession && !connection.subscribed(vehicleId)) {
            return false;
        }
        connection.subscribe(vehicleId, simNo);
        bySimNo.computeIfAbsent(simNo, key -> new ConcurrentHashMap<>()).put(sessionId, connection);
        return true;
    }

    public void unsubscribe(String sessionId, Long vehicleId) {
        MonitorConnection connection = connections.get(sessionId);
        if (connection == null) {
            return;
        }
        String simNo = connection.unsubscribe(vehicleId);
        if (simNo != null) {
            removeFromIndex(simNo, sessionId);
        }
    }

    public Collection<MonitorConnection> connections() {
        return List.copyOf(connections.values());
    }

    public Long userIdOf(String sessionId) {
        MonitorConnection connection = connections.get(sessionId);
        return connection == null ? null : connection.userId();
    }

    public void sendFrame(String sessionId, String payload) {
        MonitorConnection connection = connections.get(sessionId);
        if (connection != null) {
            send(connection, payload);
        }
    }

    public int connectionCount() {
        return connections.size();
    }

    /**
     * 广播一条位置。没有订阅者时直接返回，走不到序列化。
     */
    public void dispatch(PositionBroadcast event) {
        Map<String, MonitorConnection> subscribers = bySimNo.get(event.simNo());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (MonitorConnection connection : new ArrayList<>(subscribers.values())) {
            Long vehicleId = connection.vehicleIdOf(event.simNo());
            if (vehicleId == null) {
                continue;
            }
            send(connection, MonitorFrames.write(MonitorFrames.position(event, vehicleId, true)));
        }
    }

    public void touch(String sessionId) {
        MonitorConnection connection = connections.get(sessionId);
        if (connection != null) {
            connection.touch();
        }
    }

    public void touchAccountCheck(String sessionId) {
        MonitorConnection connection = connections.get(sessionId);
        if (connection != null) {
            connection.touchAccountCheck();
        }
    }

    /**
     * 主动断开并清理。会话失效、账号被禁用、心跳超时都走这里，保证连接与索引同时消失。
     *
     * <p>先摘索引再关连接、且不再尝试发错误帧：推送失败时也会进到这里，
     * 如果关连接前还发一帧，失败—关闭—再失败会把监听线程拖成递归。
     */
    public void close(String sessionId, CloseStatus status, String message) {
        MonitorConnection connection = connections.get(sessionId);
        if (connection == null) {
            return;
        }
        unregister(sessionId);
        WebSocketSession session = connection.session();
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.close(status);
                }
            } catch (IOException | IllegalStateException ex) {
                log.debug("关闭实时监控连接失败, sessionId={}, reason={}", sessionId, message, ex);
            }
        }
    }

    private void send(MonitorConnection connection, String payload) {
        WebSocketSession session = connection.session();
        if (!session.isOpen()) {
            unregister(session.getId());
            return;
        }
        try {
            // WebSocketSession 不是线程安全的：广播线程与会话复核线程可能同时写同一个连接。
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException | IllegalStateException ex) {
            log.debug("推送失败，按断开处理, sessionId={}", session.getId(), ex);
            close(session.getId(), CloseStatus.SESSION_NOT_RELIABLE, "推送失败");
        }
    }

    private void removeFromIndex(String simNo, String sessionId) {
        Map<String, MonitorConnection> subscribers = bySimNo.get(simNo);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(sessionId);
        if (subscribers.isEmpty()) {
            bySimNo.remove(simNo, subscribers);
        }
    }

    /**
     * 单个连接的状态。订阅集合只在连接自己的锁里改，跨连接的读取走索引。
     */
    public static final class MonitorConnection {

        private final WebSocketSession session;

        private final String sessionId;

        /** WebSocket 连接 ID 与 HTTP 会话 ID 是两回事，会话复核只能用后者。 */
        private final String httpSessionId;

        private final Long userId;

        /** vehicleId → simNo。 */
        private final Map<Long, String> subscriptions = new HashMap<>();

        private volatile long lastSeenMillis = System.currentTimeMillis();

        private volatile long lastAccountCheckMillis = System.currentTimeMillis();

        MonitorConnection(WebSocketSession session, Long userId, String httpSessionId) {
            this.session = session;
            this.sessionId = session.getId();
            this.httpSessionId = httpSessionId;
            this.userId = userId;
        }

        public WebSocketSession session() {
            return session;
        }

        public String sessionId() {
            return sessionId;
        }

        public String httpSessionId() {
            return httpSessionId;
        }

        public Long userId() {
            return userId;
        }

        public long lastSeenMillis() {
            return lastSeenMillis;
        }

        public long lastAccountCheckMillis() {
            return lastAccountCheckMillis;
        }

        synchronized void subscribe(Long vehicleId, String simNo) {
            subscriptions.put(vehicleId, simNo);
        }

        synchronized String unsubscribe(Long vehicleId) {
            return subscriptions.remove(vehicleId);
        }

        synchronized boolean subscribed(Long vehicleId) {
            return subscriptions.containsKey(vehicleId);
        }

        synchronized int subscribedCount() {
            return subscriptions.size();
        }

        synchronized Long vehicleIdOf(String simNo) {
            for (Map.Entry<Long, String> entry : subscriptions.entrySet()) {
                if (entry.getValue().equals(simNo)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        synchronized List<String> subscribedSimNos() {
            return new ArrayList<>(subscriptions.values());
        }

        void touch() {
            lastSeenMillis = System.currentTimeMillis();
        }

        void touchAccountCheck() {
            lastAccountCheckMillis = System.currentTimeMillis();
        }
    }
}
