package com.cm.admin.monitor;

import com.cm.infrastructure.telemetry.PositionBroadcast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorSessionRegistryTest {

    private static final String SESSION_ID = "s-1";

    private static final String HTTP_SESSION_ID = "http-1";

    private static final String SIM_NO = "13800138000";

    private static final long VEHICLE_ID = 100000000000000001L;

    @Mock
    private WebSocketSession session;

    private MonitorSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MonitorSessionRegistry();
        when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("订阅后广播按 SIM 命中连接，帧里带订阅时的 vehicleId")
    void should_dispatch_to_subscribed_session() throws Exception {
        when(session.isOpen()).thenReturn(true);
        registry.register(session, 1L, HTTP_SESSION_ID);
        assertTrue(registry.subscribe(SESSION_ID, VEHICLE_ID, SIM_NO, 10));

        registry.dispatch(event(SIM_NO));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"vehicleId\":\"" + VEHICLE_ID + "\""),
                captor.getValue().getPayload());
    }

    @Test
    @DisplayName("没有订阅的连接收不到广播")
    void should_not_dispatch_to_unsubscribed_session() throws Exception {
        registry.register(session, 1L, HTTP_SESSION_ID);
        registry.subscribe(SESSION_ID, VEHICLE_ID, SIM_NO, 10);
        registry.unsubscribe(SESSION_ID, VEHICLE_ID);

        registry.dispatch(event(SIM_NO));

        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("订阅上限按连接计，重复订阅同一辆不占额度")
    void should_enforce_subscribe_limit() {
        registry.register(session, 1L, HTTP_SESSION_ID);

        assertTrue(registry.subscribe(SESSION_ID, VEHICLE_ID, SIM_NO, 1));
        assertTrue(registry.subscribe(SESSION_ID, VEHICLE_ID, SIM_NO, 1));
        assertFalse(registry.subscribe(SESSION_ID, 2L, "13800138001", 1));
    }

    @Test
    @DisplayName("连接关闭后索引一并清理")
    void should_drop_subscriptions_on_close() throws Exception {
        registry.register(session, 1L, HTTP_SESSION_ID);
        registry.subscribe(SESSION_ID, VEHICLE_ID, SIM_NO, 10);

        registry.unregister(SESSION_ID);
        registry.dispatch(event(SIM_NO));

        verify(session, never()).sendMessage(any());
    }

    private PositionBroadcast event(String simNo) {
        return new PositionBroadcast(1, simNo, 30000000, 120000000, 0, 90, 0, 1, "2026-11-15T04:00:00Z");
    }
}
