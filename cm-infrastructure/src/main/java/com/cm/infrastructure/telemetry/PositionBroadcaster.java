package com.cm.infrastructure.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 实时位置广播。写完最新位置后向 Redis 频道投一条精简事件，管理端订阅后按订阅关系扇出到 WebSocket。
 *
 * <p>广播失败只计数不抛出：定位链路的落库、去重与在线判定都不该被「没人看页面」拖垮，
 * Redis 订阅本身也不提供缓冲，重连后的首屏仍然由最新位置快照兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionBroadcaster {

    private final StringRedisTemplate redisTemplate;

    private final TelemetryProperties properties;

    private final TelemetryMetrics metrics;

    public void broadcast(PositionEvent event) {
        if (!properties.getBroadcast().isEnabled()) {
            return;
        }
        try {
            redisTemplate.convertAndSend(properties.getBroadcast().getChannel(),
                    TelemetryJson.write(PositionBroadcast.of(event)));
            metrics.broadcastSent();
        } catch (RuntimeException ex) {
            metrics.broadcastFailed();
            log.warn("实时位置广播失败, simNo={}", event.simNo(), ex);
        }
    }
}
