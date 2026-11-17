package com.cm.infrastructure.command;

import tools.jackson.databind.json.JsonMapper;

/**
 * 指令链路共用的 JSON 编解码。
 * 只用版本化 JSON DTO，不使用 Java 对象序列化；解析失败按不可恢复异常上抛，
 * 由调用方决定进入死信还是拒绝，不做静默兜底。
 */
public final class CommandJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CommandJson() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
