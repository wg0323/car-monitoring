package com.cm.infrastructure.command;

/**
 * command.downlink.{gatewayId} 的消息体，字段对齐方案 11.2 的统一事件字段。
 * 事件 ID 与 commandId 同值且重发不变；attemptNo 与终端流水号由网关写入时分配，
 * 因此不在这里出现，避免两个进程对同一次尝试给出不同编号。
 */
public record CommandDownlinkEvent(int schemaVersion, String eventId, String eventType, String commandId,
                                   String simNo, String gatewayId, int commandType, String commandCode,
                                   String commandData, String traceId, String publishedAt) {

    public static final int SCHEMA_VERSION = 1;

    public static final String EVENT_TYPE = "COMMAND_DOWNLINK";
}
