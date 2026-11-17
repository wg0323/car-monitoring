package com.cm.admin.config;

import com.cm.admin.monitor.MonitorHandshakeInterceptor;
import com.cm.admin.monitor.MonitorPositionSubscriber;
import com.cm.admin.monitor.MonitorWebSocketHandler;
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
 * 实时监控通道装配：WebSocket 端点 /ws/monitor 与定位广播订阅。
 *
 * <p>端点放在管理端而不是网关：订阅校验要用登录会话与数据范围，这两样只有管理端持有。
 * 单连接订阅上限、心跳与会话复核间隔都在 {@link MonitorProperties} 里配置。
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(MonitorProperties.class)
@RequiredArgsConstructor
public class MonitorWebSocketConfig implements WebSocketConfigurer {

    private static final String ENDPOINT = "/ws/monitor";

    private final MonitorWebSocketHandler handler;

    private final MonitorHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origin 校验统一放在握手拦截器里：Spring 默认的同源判定看不懂 Vite 代理后的 Host，
        // 两套规则并存时只会让开发环境握手莫名失败。
        registry.addHandler(handler, ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public RedisMessageListenerContainer monitorPositionListenerContainer(RedisConnectionFactory connectionFactory,
                                                                         MonitorPositionSubscriber subscriber,
                                                                         MonitorProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(properties.getPositionChannel()));
        return container;
    }
}
