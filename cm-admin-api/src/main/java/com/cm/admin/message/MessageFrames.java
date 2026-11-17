package com.cm.admin.message;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 消息中心帧定义与编解码。上行只有聊天与心跳两种帧，下行是聊天、系统广播、错误与 pong。
 *
 * <p>用户 ID 一律用字符串：雪花 ID 是 19 位十进制，走 JSON number 会在浏览器里丢精度，
 * 聊天发起方与接收方会对不上号。
 */
final class MessageFrames {

    static final String TYPE_CHAT = "CHAT";

    static final String TYPE_SYSTEM = "SYSTEM";

    static final String TYPE_PING = "PING";

    static final String TYPE_PONG = "PONG";

    static final String TYPE_ERROR = "ERROR";

    static final int CODE_BAD_FRAME = 4400;

    static final int CODE_UNAUTHORIZED = 4401;

    static final int CODE_OFFLINE = 4404;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private MessageFrames() {
    }

    static InboundFrame read(String payload) {
        return MAPPER.readValue(payload, InboundFrame.class);
    }

    static DeliveryFrame readDelivery(String payload) {
        return MAPPER.readValue(payload, DeliveryFrame.class);
    }

    static String write(Object frame) {
        return MAPPER.writeValueAsString(frame);
    }

    static DeliveryFrame chat(long fromUserId, String fromName, long toUserId, String text) {
        return new DeliveryFrame(TYPE_CHAT, String.valueOf(fromUserId), fromName, String.valueOf(toUserId),
                text, now());
    }

    static DeliveryFrame system(String text) {
        return new DeliveryFrame(TYPE_SYSTEM, null, null, null, text, now());
    }

    static ErrorFrame error(int code, String message) {
        return new ErrorFrame(TYPE_ERROR, code, message);
    }

    static PongFrame pong() {
        return new PongFrame(TYPE_PONG);
    }

    private static String now() {
        return TIME_FORMATTER.format(LocalDateTime.now());
    }

    /** 客户端上行帧：聊天带接收人与文本，心跳只用 type。 */
    record InboundFrame(String type, String toUserId, String text) {
    }

    /** 下行帧，同时也是写进 Redis 频道的载荷：toUserId 为空的聊天帧不会被任何实例投递。 */
    record DeliveryFrame(String type, String fromUserId, String fromName, String toUserId, String text,
                         String timestamp) {
    }

    record ErrorFrame(String type, int code, String message) {
    }

    record PongFrame(String type) {
    }
}
