package com.cm.admin.monitor;

import com.cm.admin.config.MonitorProperties;
import com.cm.admin.service.MonitorAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 实时监控 WebSocket 处理器。上行只有订阅、退订与心跳三种帧，
 * 车辆是否可推一律在订阅时按服务端数据范围判定，推送阶段不再相信客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private final MonitorSessionRegistry registry;

    private final MonitorAppService monitorAppService;

    private final MonitorProperties properties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userId = session.getAttributes().get(MonitorHandshakeInterceptor.ATTR_USER_ID);
        Object httpSessionId = session.getAttributes().get(MonitorHandshakeInterceptor.ATTR_HTTP_SESSION_ID);
        if (!(userId instanceof Long id) || !(httpSessionId instanceof String sessionKey) || sessionKey.isBlank()) {
            // 这时连接还没进注册表，registry.close 会被直接 return，必须自己关。
            closeQuietly(session, "未认证的连接");
            return;
        }
        registry.register(session, id, sessionKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        registry.touch(session.getId());
        MonitorFrames.InboundFrame frame;
        try {
            frame = MonitorFrames.read(message.getPayload());
        } catch (RuntimeException ex) {
            log.debug("实时监控收到非法帧, sessionId={}", session.getId(), ex);
            sendError(session, MonitorFrames.CODE_BAD_FRAME, "帧格式不正确");
            return;
        }
        if (frame == null || frame.type() == null) {
            sendError(session, MonitorFrames.CODE_BAD_FRAME, "缺少 type");
            return;
        }
        switch (frame.type()) {
            case MonitorFrames.TYPE_SUBSCRIBE -> handleSubscribe(session, frame.vehicleIds());
            case MonitorFrames.TYPE_UNSUBSCRIBE -> handleUnsubscribe(session, frame.vehicleIds());
            case MonitorFrames.TYPE_PING -> registry.sendFrame(session.getId(),
                    MonitorFrames.write(MonitorFrames.pong()));
            default -> sendError(session, MonitorFrames.CODE_BAD_FRAME, "未知的指令类型：" + frame.type());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("实时监控传输异常, sessionId={}", session.getId(), exception);
        registry.unregister(session.getId());
    }

    private void handleSubscribe(WebSocketSession session, List<String> rawVehicleIds) {
        Long userId = registry.userIdOf(session.getId());
        if (userId == null) {
            registry.close(session.getId(), new CloseStatus(MonitorFrames.CODE_UNAUTHORIZED, "连接已失效"),
                    "连接已失效");
            return;
        }
        List<Long> requested = parseVehicleIds(session, rawVehicleIds);
        if (requested.isEmpty()) {
            return;
        }
        int limit = properties.getSubscribe().getMaxVehiclesPerSession();
        if (requested.size() > limit) {
            // 先卡数量再查库：否则一个账号可以靠连续发帧把 IN 列表与查询次数无限放大。
            sendError(session, MonitorFrames.CODE_LIMIT_EXCEEDED, "单连接订阅数量超过 " + limit);
            return;
        }
        Map<Long, String> allowed = monitorAppService.vehiclesInScope(userId, requested);
        int subscribed = 0;
        for (Map.Entry<Long, String> entry : allowed.entrySet()) {
            if (!registry.subscribe(session.getId(), entry.getKey(), entry.getValue(), limit)) {
                sendError(session, MonitorFrames.CODE_LIMIT_EXCEEDED, "单连接订阅数量超过 " + limit);
                break;
            }
            subscribed++;
        }
        if (allowed.size() < new LinkedHashSet<>(requested).size()) {
            // 越界的车既不报错断开也不回数据：只告诉页面「部分车辆已忽略」，避免用订阅探测他人车辆是否存在。
            sendError(session, MonitorFrames.CODE_OUT_OF_SCOPE, "部分车辆不在当前账号的数据范围内，已忽略");
        }
        log.debug("实时监控订阅, sessionId={}, userId={}, 请求={}, 接受={}", session.getId(), userId,
                requested.size(), subscribed);
    }

    private void handleUnsubscribe(WebSocketSession session, List<String> rawVehicleIds) {
        for (Long vehicleId : parseVehicleIds(session, rawVehicleIds)) {
            registry.unsubscribe(session.getId(), vehicleId);
        }
    }

    private List<Long> parseVehicleIds(WebSocketSession session, List<String> rawVehicleIds) {
        if (rawVehicleIds == null || rawVehicleIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(rawVehicleIds.size());
        for (String raw : rawVehicleIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(raw.trim()));
            } catch (NumberFormatException ex) {
                log.debug("实时监控订阅收到非法车辆 ID, sessionId={}, value={}", session.getId(), raw);
                sendError(session, MonitorFrames.CODE_BAD_FRAME, "车辆 ID 格式不正确");
                return List.of();
            }
        }
        return ids;
    }

    private void sendError(WebSocketSession session, int code, String message) {
        registry.sendFrame(session.getId(), MonitorFrames.write(MonitorFrames.error(code, message)));
    }

    private void closeQuietly(WebSocketSession session, String reason) {
        try {
            session.close(new CloseStatus(MonitorFrames.CODE_UNAUTHORIZED, reason));
        } catch (IOException | IllegalStateException ex) {
            log.debug("关闭未认证连接失败, sessionId={}", session.getId(), ex);
        }
    }
}
