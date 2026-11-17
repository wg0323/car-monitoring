package com.cm.admin.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 连接表覆盖：多标签页各自独立、私聊只投目标用户、注销一个标签页不影响另一个、
 * 在线集合的进入与退出都跟着连接生命周期走。
 */
@ExtendWith(MockitoExtension.class)
class MessageSessionRegistryTest {

    private static final long USER_ID = 7L;

    @Mock
    private MessagePresence presence;

    private MessageSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MessageSessionRegistry(presence);
    }

    @Test
    @DisplayName("注册后连接可投递，注销后在线集合同步清理")
    void should_register_and_unregister_with_presence() throws Exception {
        WebSocketSession session = session("s1");
        when(presence.online(USER_ID)).thenReturn(true);
        registry.register(session, USER_ID, "监控员一", "hs1");

        assertTrue(registry.online(USER_ID), "在线判定交给 Redis 在线集合，由 mock 返回");
        registry.sendToUser(USER_ID, "{\"type\":\"SYSTEM\",\"text\":\"hi\"}");
        verify(session).sendMessage(new TextMessage("{\"type\":\"SYSTEM\",\"text\":\"hi\"}"));

        registry.unregister("s1");
        verify(presence).leave(USER_ID, "监控员一", "hs1");
        assertEquals(0, registry.connections().size());
    }

    @Test
    @DisplayName("同一账号两个标签页都收到私聊，关掉一个另一个仍在线")
    void should_deliver_to_every_tab_of_same_user() throws Exception {
        WebSocketSession first = session("s1");
        WebSocketSession second = session("s2");
        registry.register(first, USER_ID, "监控员一", "hs1");
        registry.register(second, USER_ID, "监控员一", "hs2");

        registry.sendToUser(USER_ID, "payload");
        verify(first).sendMessage(new TextMessage("payload"));
        verify(second).sendMessage(new TextMessage("payload"));

        registry.unregister("s1");
        registry.sendToUser(USER_ID, "payload2");
        verify(second).sendMessage(new TextMessage("payload2"));
        verify(first, never()).sendMessage(new TextMessage("payload2"));
        assertEquals(1, registry.connections().size());
    }

    @Test
    @DisplayName("心跳同时刷新 Redis 在线集合")
    void should_refresh_presence_on_touch() throws Exception {
        WebSocketSession session = session("s1");
        registry.register(session, USER_ID, "监控员一", "hs1");

        registry.touch("s1");

        // 注册一次 + 心跳一次
        verify(presence, times(2)).join(eq(USER_ID), eq("监控员一"), eq("hs1"), anyLong());
    }

    @Test
    @DisplayName("未注册的连接心跳与投递都是空操作")
    void should_ignore_unknown_session() throws Exception {
        registry.touch("unknown");
        registry.sendToUser(USER_ID, "payload");

        verify(presence, never()).join(anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), anyLong());
    }

    @Test
    @DisplayName("广播推给本机全部连接")
    void should_broadcast_to_all_connections() throws Exception {
        WebSocketSession first = session("s1");
        WebSocketSession second = session("s2");
        registry.register(first, 1L, "甲", "hs1");
        registry.register(second, 2L, "乙", "hs2");

        registry.broadcast("system");

        verify(first).sendMessage(new TextMessage("system"));
        verify(second).sendMessage(new TextMessage("system"));
    }

    private WebSocketSession session(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        // 只推进在线集合的用例不会走到发送，isOpen 允许宽松桩。
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }
}
