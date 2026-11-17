package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import com.cm.admin.message.MessageFrames.InboundFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 聊天上行处理。校验接收人、文本长度与对方在线状态后，统一交给 Redis 频道扇出：
 * 即便同机有两个连接，也是「发布 → 订阅 → 投递」一条路径，不做本地直投，避免双发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHub {

    private final MessageSessionRegistry registry;

    private final MessageDelivery delivery;

    private final MessageProperties properties;

    void handle(WebSocketSession session, String payload) {
        registry.touch(session.getId());
        InboundFrame frame;
        try {
            frame = MessageFrames.read(payload);
        } catch (RuntimeException ex) {
            log.debug("消息中心收到非法帧, sessionId={}", session.getId(), ex);
            error(session, MessageFrames.CODE_BAD_FRAME, "帧格式不正确");
            return;
        }
        if (frame == null || frame.type() == null) {
            error(session, MessageFrames.CODE_BAD_FRAME, "缺少 type");
            return;
        }
        switch (frame.type()) {
            case MessageFrames.TYPE_PING -> registry.sendFrame(session.getId(),
                    MessageFrames.write(MessageFrames.pong()));
            case MessageFrames.TYPE_CHAT -> handleChat(session, frame);
            default -> error(session, MessageFrames.CODE_BAD_FRAME, "未知的指令类型：" + frame.type());
        }
    }

    private void handleChat(WebSocketSession session, InboundFrame frame) {
        Long fromUserId = registry.userIdOf(session.getId());
        if (fromUserId == null) {
            registry.close(session.getId(), new CloseStatus(MessageFrames.CODE_UNAUTHORIZED, "连接已失效"),
                    "连接已失效");
            return;
        }
        Long toUserId = parseUserId(session, frame.toUserId());
        if (toUserId == null) {
            return;
        }
        if (toUserId.equals(fromUserId)) {
            error(session, MessageFrames.CODE_BAD_FRAME, "不能给自己发消息");
            return;
        }
        String text = frame.text() == null ? "" : frame.text().trim();
        if (text.isEmpty()) {
            error(session, MessageFrames.CODE_BAD_FRAME, "消息内容不能为空");
            return;
        }
        if (text.length() > properties.getMaxTextLength()) {
            error(session, MessageFrames.CODE_BAD_FRAME,
                    "单条消息不能超过 " + properties.getMaxTextLength() + " 字");
            return;
        }
        if (!registry.online(toUserId)) {
            error(session, MessageFrames.CODE_OFFLINE, "对方不在线");
            return;
        }
        String fromName = registry.displayNameOf(session.getId());
        delivery.publishChat(fromUserId, fromName, toUserId, text);
        log.debug("私聊已投递, from={}, to={}", fromUserId, toUserId);
    }

    private Long parseUserId(WebSocketSession session, String raw) {
        if (raw == null || raw.isBlank()) {
            error(session, MessageFrames.CODE_BAD_FRAME, "接收人不能为空");
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            error(session, MessageFrames.CODE_BAD_FRAME, "接收人格式不正确");
            return null;
        }
    }

    private void error(WebSocketSession session, int code, String message) {
        registry.sendFrame(session.getId(), MessageFrames.write(MessageFrames.error(code, message)));
    }
}
  
