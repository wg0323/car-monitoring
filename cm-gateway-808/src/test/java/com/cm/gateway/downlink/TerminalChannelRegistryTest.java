package com.cm.gateway.downlink;

import com.cm.transport.ChannelRoute;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同 SIM 顶号语义：新连接覆盖旧连接、旧连接迟到关闭不删新连接、
 * 同一连接重新鉴权沿用下行流水号。
 */
class TerminalChannelRegistryTest {

    private static final String SIM_NO = "13800138000";

    private static final String GATEWAY_ID = "gw-test";

    private final TerminalChannelRegistry registry = new TerminalChannelRegistry();

    @Test
    @DisplayName("同一 SIM 再次绑定后，路由与通道指向新连接")
    void should_point_to_new_connection_after_rebind() {
        registry.bind(route("conn-old", 1L), new EmbeddedChannel());

        ChannelRoute newRoute = route("conn-new", 2L);
        EmbeddedChannel newChannel = new EmbeddedChannel();
        registry.bind(newRoute, newChannel);

        TerminalChannelRegistry.TerminalConnection current = registry.find(SIM_NO).orElseThrow();
        assertEquals(newRoute, current.route());
        assertSame(newChannel, current.channel());
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("旧连接迟到关闭不能删掉新连接")
    void should_keep_new_connection_when_old_channel_closes_late() {
        ChannelRoute oldRoute = route("conn-old", 1L);
        registry.bind(oldRoute, new EmbeddedChannel());
        ChannelRoute newRoute = route("conn-new", 2L);
        registry.bind(newRoute, new EmbeddedChannel());

        assertFalse(registry.unbind(oldRoute), "旧连接不是当前路由，不应解绑");
        assertTrue(registry.find(SIM_NO).isPresent(), "新连接仍应留在连接表");

        assertTrue(registry.unbind(newRoute));
        assertTrue(registry.find(SIM_NO).isEmpty());
    }

    @Test
    @DisplayName("同一通道重新鉴权沿用下行流水号，不从头开始")
    void should_keep_serial_no_when_same_channel_rebinds() {
        EmbeddedChannel channel = new EmbeddedChannel();
        registry.bind(route("conn-1", 1L), channel);
        int before = registry.find(SIM_NO).orElseThrow().nextSerialNo();

        registry.bind(route("conn-1", 2L), channel);

        int after = registry.find(SIM_NO).orElseThrow().nextSerialNo();
        assertEquals(before + 1, after);
    }

    @Test
    @DisplayName("连接关闭后不再报告 active")
    void should_report_inactive_after_channel_closed() {
        EmbeddedChannel channel = new EmbeddedChannel();
        registry.bind(route("conn-1", 1L), channel);
        assertTrue(registry.find(SIM_NO).orElseThrow().active());

        channel.close();

        assertFalse(registry.find(SIM_NO).orElseThrow().active());
    }

    private static ChannelRoute route(String channelId, long incarnation) {
        return new ChannelRoute(SIM_NO, GATEWAY_ID, channelId, incarnation);
    }
}
