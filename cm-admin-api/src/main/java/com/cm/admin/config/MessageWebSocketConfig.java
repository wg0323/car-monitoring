package com.cm.admin.config;

import com.cm.admin.message.MessageHandshakeInterceptor;
import com.cm.admin.message.MessageSubscriber;
import com.cm.admin.message.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 消息中心通道装配：WebSocket 端点 /ws/message 与 Redis 频道订阅。
 *
 * <p>端点放在管理端而不是网关：聊天要读登录会话、在线集合与权限码，这三样只有管理端持有。
 * 频道订阅与实时监控的定位广播同构，只是一个按 SIM 扇出、一个按用户扇出。
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(MessageProperties.class)
@RequiredArgsConstructor
public class MessageWebSocketConfig implements WebSocketConfigurer {

    private static final String ENDPOINT = "/ws/message";

    private final MessageWebSocketHandler handler;

    private final MessageHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origin 校验统一放在握手拦截器里：Spring 默认的同源判定看不懂 Vite 代理后的 Host。
        registry.addHandler(handler, ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public RedisMessageListenerContainer messageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                  MessageSubscriber subscriber,
                                                                  MessageProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(properties.getChannel()));
        return container;
    }
}
