package com.cm.admin.monitor;

import com.cm.infrastructure.telemetry.PositionBroadcast;
import com.cm.infrastructure.telemetry.TelemetryJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 定位广播订阅者。网关写完最新位置后往 Redis 频道投一条精简事件，这里收到后按订阅关系扇出。
 * 解析失败只丢这一条：广播是尽力而为的 UI 通道，坏消息不该影响后续推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorPositionSubscriber implements MessageListener {

    private final MonitorSessionRegistry registry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        PositionBroadcast event;
        try {
            event = TelemetryJson.read(payload, PositionBroadcast.class);
        } catch (RuntimeException ex) {
            log.warn("实时位置广播解析失败，已丢弃", ex);
            return;
        }
        if (event == null || event.simNo() == null) {
            return;
        }
        if (event.schemaVersion() != PositionBroadcast.SCHEMA_VERSION) {
            // 网关与 admin 可以独立发布：版本不一致时宁可少推，也不要把新语义当旧语义推给页面。
            log.warn("实时位置广播版本不支持，已丢弃, schemaVersion={}", event.schemaVersion());
            return;
        }
        registry.dispatch(event);
    }
}
