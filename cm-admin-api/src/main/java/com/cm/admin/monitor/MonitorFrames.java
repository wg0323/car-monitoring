package com.cm.admin.monitor;

import com.cm.infrastructure.telemetry.PositionBroadcast;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * WebSocket 帧定义与编解码。车辆 ID 一律用字符串：雪花 ID 是 19 位十进制，
 * 走 JSON number 会在浏览器里丢精度，订阅与回推都会对不上号。
 */
final class MonitorFrames {

    static final String TYPE_SUBSCRIBE = "SUBSCRIBE";

    static final String TYPE_UNSUBSCRIBE = "UNSUBSCRIBE";

    static final String TYPE_PING = "PING";

    static final String TYPE_POSITION = "POSITION";

    static final String TYPE_PONG = "PONG";

    static final String TYPE_ERROR = "ERROR";

    static final int CODE_UNAUTHORIZED = 4401;

    static final int CODE_OUT_OF_SCOPE = 4403;

    static final int CODE_BAD_FRAME = 4400;

    static final int CODE_LIMIT_EXCEEDED = 4429;

    /** 808 经纬度为百万分之一度，速度为 0.1 公里/小时；帧里统一换成度与公里/小时。 */
    private static final double DEGREE_SCALE = 1_000_000.0;

    private static final double SPEED_SCALE = 10.0;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private MonitorFrames() {
    }

    static InboundFrame read(String payload) {
        return MAPPER.readValue(payload, InboundFrame.class);
    }

    static String write(Object frame) {
        return MAPPER.writeValueAsString(frame);
    }

    static PositionFrame position(PositionBroadcast event, Long vehicleId, boolean online) {
        return new PositionFrame(TYPE_POSITION, String.valueOf(vehicleId), event.simNo(),
                event.latitude() / DEGREE_SCALE, event.longitude() / DEGREE_SCALE,
                Math.round(event.speed() / SPEED_SCALE * 10.0) / 10.0, event.course(), event.deviceTime(), online);
    }

    static ErrorFrame error(int code, String message) {
        return new ErrorFrame(TYPE_ERROR, code, message);
    }

    static PongFrame pong() {
        return new PongFrame(TYPE_PONG);
    }

    /**
     * 客户端上行帧。vehicleIds 用字符串接收：前端拿到的是字符串 ID，回传时也是字符串。
     */
    record InboundFrame(String type, List<String> vehicleIds) {
    }

    record PositionFrame(String type, String vehicleId, String simNo, Double latitude, Double longitude,
                         Double speedKph, Integer course, String deviceTime, boolean online) {
    }

    record ErrorFrame(String type, int code, String message) {
    }

    record PongFrame(String type) {
    }
}
