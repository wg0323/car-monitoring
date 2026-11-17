package com.cm.infrastructure.telemetry;

import java.util.Optional;

/**
 * 在线租约的值：网关实例与连接代号。旧连接不能续租或删除新连接的租约。
 */
public record OnlineLease(String gatewayId, long incarnation) {

    private static final String SEPARATOR = "|";

    private static final int MIN_PARTS = 2;

    public static Optional<OnlineLease> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|");
        if (parts.length < MIN_PARTS) {
            return Optional.empty();
        }
        try {
            return Optional.of(new OnlineLease(parts[0], Long.parseLong(parts[1])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public String format() {
        return gatewayId + SEPARATOR + incarnation;
    }
}
