package com.cm.protocol.jt809;

/**
 * 已解码的 JT809 帧。body 为未转义的原始消息体，业务字段解析交给 body 包的记录类；
 * 帧头字段原样保留，便于回写审计日志和排查对端实现差异。
 */
public record Jt809Frame(int messageId, long messageSn, long centerId, byte[] version,
                         int encryptFlag, long encryptKey, byte[] body) {

    public int bodyLength() {
        return body.length;
    }
}

