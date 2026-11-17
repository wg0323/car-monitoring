package com.cm.infrastructure.command;

/**
 * Redis 保存的终端路由：simNo → gatewayId + incarnation。
 * 只存下行必需的网关标识与连接代号，值格式固定为 {@code gatewayId|incarnation}，
 * 不额外引入序列化器，便于运维直接 redis-cli 查看。
 */
public record TerminalRoute(String gatewayId, long incarnation) {

    private static final String SEPARATOR = "|";

    private static final int PART_COUNT = 2;

    public TerminalRoute {
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new IllegalArgumentException("gatewayId 不能为空");
        }
    }

    public String format() {
        return gatewayId + SEPARATOR + incarnation;
    }

    /**
     * 解析路由值；格式非法返回 null，由调用方按“无路由”处理，不猜测网关。
     */
    public static TerminalRoute parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\" + SEPARATOR, -1);
        if (parts.length != PART_COUNT || parts[0].isBlank()) {
            return null;
        }
        try {
            return new TerminalRoute(parts[0], Long.parseLong(parts[1]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
