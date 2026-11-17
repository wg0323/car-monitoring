package com.cm.infrastructure.telemetry;

/**
 * 定位事件。时间字段统一用 ISO-8601 字符串跨进程传递，避免时区与 Jackson 时间模块差异。
 * eventId 由接入网关按原始帧确定性生成，MQ 重发、消费重试与死信重放都沿用同一个值。
 */
public record PositionEvent(int schemaVersion, String eventId, String eventType, String protocolBranch,
                            String simNo, int deviceSeq, String deviceTime, String ingestTime,
                            String effectiveDeviceTime, String timeQuality, String gatewayId,
                            String traceId, int batchIndex, PositionSample sample, String payloadDigest) {

    public static final int SCHEMA_VERSION = 1;

    public static final String EVENT_TYPE = "POSITION";

    public static final String BRANCH_STANDARD = "STANDARD_0200";

    public static final String BRANCH_BATCH = "BATCH_0704";

    public static final String BRANCH_PRIVATE = "PRIVATE_DOLLAR";
}
