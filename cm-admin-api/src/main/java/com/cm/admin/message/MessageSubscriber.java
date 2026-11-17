package com.cm.admin.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 消息通道订阅者。收到本实例或其它实例发布的消息后，按类型投递给本机连接：
 * 私聊只找目标用户，系统广播推给全部在线连接。解析失败只丢这一条，
 * 通道是尽力而为的 UI 能力，坏消息不该影响后续投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSubscriber implements MessageListener {

    private final MessageSessionRegistry registry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        MessageFrames.DeliveryFrame frame;
        try {
            frame = MessageFrames.readDelivery(payload);
        } catch (RuntimeException ex) {
            log.warn("消息通道载荷解析失败，已丢弃", ex);
            return;
        }
        if (frame == null || frame.type() == null) {
            return;
        }
        if (MessageFrames.TYPE_SYSTEM.equals(frame.type())) {
            registry.broadcast(payload);
            return;
        }
        if (MessageFrames.TYPE_CHAT.equals(frame.type()) && frame.toUserId() != null) {
            try {
                registry.sendToUser(Long.parseLong(frame.toUserId()), payload);
            } catch (NumberFormatException ex) {
                log.warn("私聊载荷接收人格式不正确，已丢弃, toUserId={}", frame.toUserId());
            }
        }
    }
}
