package com.cm.infrastructure.telemetry;

import tools.jackson.databind.json.JsonMapper;

/**
 * 定位链路的版本化 JSON 编解码。与指令链路共用同一套 Jackson 3 配置，
 * 解析失败按不可恢复异常上抛，由消费端决定进入死信。
 */
public final class TelemetryJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TelemetryJson() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
