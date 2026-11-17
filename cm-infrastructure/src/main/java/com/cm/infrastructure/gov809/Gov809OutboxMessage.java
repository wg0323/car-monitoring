package com.cm.infrastructure.gov809;

/**
 * 待积压的 809 报文。payloadHex 是已编码的消息体十六进制，
 * 断链期间只存这一份，重连补发时不再回查业务数据。
 */
public record Gov809OutboxMessage(long platformId, String bizEventId, int msgType, int subType,
                                  String plateNo, int plateColor, String payloadHex) {
}
 
