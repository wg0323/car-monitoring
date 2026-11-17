package com.cm.admin.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 消息中心端点。上行只有聊天与心跳，是否可聊（对方在线、文本长度）一律在服务端判定，
 * 客户端自带的发起人字段一概不看。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final MessageSessionRegistry registry;

    private final MessageHub hub;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userId = session.getAttributes().get(MessageHandshakeInterceptor.ATTR_USER_ID);
        Object displayName = session.getAttributes().get(MessageHandshakeInterceptor.ATTR_DISPLAY_NAME);
        Object httpSessionId = session.getAttributes().get(MessageHandshakeInterceptor.ATTR_HTTP_SESSION_ID);
        if (!(userId instanceof Long id) || !(httpSessionId instanceof String sessionKey) || sessionKey.isBlank()) {
            // 这时连接还没进注册表，registry.close 会被直接 return，必须自己关。
            closeQuietly(session, "未认证的连接");
            return;
        }
        registry.register(session, id, displayName instanceof String name ? name : "", sessionKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        hub.handle(session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("消息中心传输异常, sessionId={}", session.getId(), exception);
        registry.unregister(session.getId());
    }

    private void closeQuietly(WebSocketSession session, String reason) {
        try {
            session.close(new CloseStatus(MessageFrames.CODE_UNAUTHORIZED, reason));
        } catch (IOException | IllegalStateException ex) {
            log.debug("关闭未认证消息连接失败, sessionId={}", session.getId(), ex);
        }
    }
}
