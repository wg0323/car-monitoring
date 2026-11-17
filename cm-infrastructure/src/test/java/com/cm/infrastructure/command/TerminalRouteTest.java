package com.cm.infrastructure.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 路由值的格式契约：网关标识 + 连接代号，格式非法一律按无路由处理。
 */
class TerminalRouteTest {

    @Test
    @DisplayName("格式化后可以原样解析")
    void should_roundtrip() {
        TerminalRoute route = new TerminalRoute("gw-1", 1730000000001L);

        TerminalRoute parsed = TerminalRoute.parse(route.format());

        assertEquals("gw-1", parsed.gatewayId());
        assertEquals(1730000000001L, parsed.incarnation());
    }

    @Test
    @DisplayName("缺失、空串、缺分隔符、代号非数字都返回空")
    void should_reject_malformed_values() {
        assertNull(TerminalRoute.parse(null));
        assertNull(TerminalRoute.parse(""));
        assertNull(TerminalRoute.parse("gw-1"));
        assertNull(TerminalRoute.parse("gw-1|"));
        assertNull(TerminalRoute.parse("gw-1|abc"));
        assertNull(TerminalRoute.parse("gw-1|1|2"));
        assertNull(TerminalRoute.parse("|123"));
    }

    @Test
    @DisplayName("网关标识为空时构造即失败，不生成不可信路由")
    void should_reject_blank_gateway() {
        assertThrows(IllegalArgumentException.class, () -> new TerminalRoute(" ", 1L));
    }
}
