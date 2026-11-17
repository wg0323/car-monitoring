package com.cm.admin.message;

import com.cm.admin.message.MessagePresence.OnlineUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天连接表。一个账号可以开多个标签页，所以按用户 ID 维护一组连接；
 * 投递只按用户找连接，在线状态与在线列表交给 {@link MessagePresence}（Redis）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSessionRegistry {

    /** sessionId → 连接。会话复核与心跳踢除都按这个索引遍历。 */
    private final Map<String, MessageConnection> connections = new ConcurrentHashMap<>();

    /** userId → (sessionId → 连接)。私聊投递按这里找本机连接。 */
    private final Map<Long, Map<String, MessageConnection>> byUser = new ConcurrentHashMap<>();

    private final MessagePresence presence;

    public void register(WebSocketSession session, Long userId, String displayName, String httpSessionId) {
        MessageConnection connection = new MessageConnection(session, userId, displayName, httpSessionId);
        connections.put(session.getId(), connection);
        byUser.computeIfAbsent(userId, key -> new ConcurrentHashMap<>()).put(session.getId(), connection);
        presence.join(userId, displayName, httpSessionId, connection.lastSeenMillis());
        log.debug("消息中心连接建立, sessionId={}, userId={}, 本机连接数={}", session.getId(), userId,
                connections.size());
    }

    public void unregister(String sessionId) {
        MessageConnection connection = connections.remove(sessionId);
        if (connection == null) {
            return;
        }
        Map<String, MessageConnection> sessions = byUser.get(connection.userId());
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                byUser.remove(connection.userId(), sessions);
            }
        }
        presence.leave(connection.userId(), connection.displayName(), connection.httpSessionId());
        log.debug("消息中心连接关闭, sessionId={}, userId={}, 本机连接数={}", sessionId, connection.userId(),
                connections.size());
    }

    public Collection<MessageConnection> connections() {
        return List.copyOf(connections.values());
    }

    public Long userIdOf(String sessionId) {
        MessageConnection connection = connections.get(sessionId);
        return connection == null ? null : connection.userId();
    }

    public String displayNameOf(String sessionId) {
        MessageConnection connection = connections.get(sessionId);
        return connection == null ? "" : connection.displayName();
    }

    /** 心跳续租：同时刷新本机时间戳与 Redis 在线集合。 */
    public void touch(String sessionId) {
        MessageConnection connection = connections.get(sessionId);
        if (connection == null) {
            return;
        }
        connection.touch();
        presence.join(connection.userId(), connection.displayName(), connection.httpSessionId(),
                connection.lastSeenMillis());
    }

    public boolean online(Long userId) {
        return presence.online(userId);
    }

    public List<OnlineUser> onlineUsers() {
        return presence.onlineUsers();
    }

    public void sendFrame(String sessionId, String payload) {
        MessageConnection connection = connections.get(sessionId);
        if (connection != null) {
            send(connection, payload);
        }
    }

    /** 私聊投递：只投本机上该用户的连接，其它实例由各自的订阅者投递。 */
    public void sendToUser(Long userId, String payload) {
        Map<String, MessageConnection> sessions = byUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (MessageConnection connection : new ArrayList<>(sessions.values())) {
            send(connection, payload);
        }
    }

    public void broadcast(String payload) {
        for (MessageConnection connection : new ArrayList<>(connections.values())) {
            send(connection, payload);
        }
    }

    /**
     * 主动断开并清理。先摘索引再关连接、且不再尝试发错误帧：
     * 推送失败时也会进到这里，关连接前再发一帧会把监听线程拖成递归。
     */
    public void close(String sessionId, CloseStatus status, String message) {
        MessageConnection connection = connections.get(sessionId);
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
                log.debug("关闭消息中心连接失败, sessionId={}, reason={}", sessionId, message, ex);
            }
        }
    }

    private void send(MessageConnection connection, String payload) {
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
            log.debug("消息推送失败，按断开处理, sessionId={}", session.getId(), ex);
            close(session.getId(), CloseStatus.SESSION_NOT_RELIABLE, "推送失败");
        }
    }

    /** 单个连接的状态。时间戳由心跳与订阅者共享，用 volatile 保证可见性。 */
    public static final class MessageConnection {

        private final WebSocketSession session;

        private final String sessionId;

        private final Long userId;

        private final String displayName;

        /** WebSocket 连接 ID 与 HTTP 会话 ID 是两回事，会话复核只能用后者。 */
        private final String httpSessionId;

        private volatile long lastSeenMillis = System.currentTimeMillis();

        MessageConnection(WebSocketSession session, Long userId, String displayName, String httpSessionId) {
            this.session = session;
            this.sessionId = session.getId();
            this.userId = userId;
            this.displayName = displayName == null ? "" : displayName;
            this.httpSessionId = httpSessionId;
        }

        public WebSocketSession session() {
            return session;
        }

        public String sessionId() {
            return sessionId;
        }

        public Long userId() {
            return userId;
        }

        public String displayName() {
            return displayName;
        }

        public String httpSessionId() {
            return httpSessionId;
        }

        public long lastSeenMillis() {
            return lastSeenMillis;
        }

        void touch() {
            lastSeenMillis = System.currentTimeMillis();
        }
    }
}
