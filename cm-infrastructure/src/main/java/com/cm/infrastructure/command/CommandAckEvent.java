package com.cm.infrastructure.command;

/**
 * command.ack 的消息体。网关只知道连接上的 SIM、流水号与 incarnation，
 * 归属由消费端按这三元组回查，事件里不带 commandId，避免网关猜测归属。
 */
public record CommandAckEvent(int schemaVersion, String eventId, String eventType, String simNo,
                              int terminalSerialNo, long connectionIncarnation, String gatewayId,
                              int ackMessageId, int ackResult, String rawHex, String receivedAt) {

    public static final int SCHEMA_VERSION = 1;

    public static final String EVENT_TYPE = "COMMAND_ACK";
}
