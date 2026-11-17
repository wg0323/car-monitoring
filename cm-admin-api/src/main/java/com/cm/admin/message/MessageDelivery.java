package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息发布：所有聊天与系统广播都先写 Redis 频道，再由各实例的订阅者投递给本机连接。
 * 不做本地直投，保证「一条消息只投一次」在单实例与多实例下是同一套路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDelivery {

    private final StringRedisTemplate redisTemplate;

    private final MessageProperties properties;

    public void publishChat(long fromUserId, String fromName, long toUserId, String text) {
        publish(MessageFrames.chat(fromUserId, fromName, toUserId, text));
    }

    public void publishSystem(String text) {
        publish(MessageFrames.system(text));
    }

    private void publish(MessageFrames.DeliveryFrame frame) {
        redisTemplate.convertAndSend(properties.getChannel(), MessageFrames.write(frame));
        log.debug("消息已发布, type={}, to={}", frame.type(), frame.toUserId());
    }
}
