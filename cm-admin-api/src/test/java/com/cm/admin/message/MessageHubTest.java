package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聊天上行处理的分支覆盖：合法聊天只发一条、离线与超长被拒、心跳回 pong、坏帧回错误帧。
 */
@ExtendWith(MockitoExtension.class)
class MessageHubTest {

    private static final String SESSION_ID = "s1";

    private static final long FROM_USER_ID = 1L;

    private static final long TO_USER_ID = 2L;

    @Mock
    private MessageSessionRegistry registry;

    @Mock
    private MessageDelivery delivery;

    private WebSocketSession session;

    private MessageHub hub;

    @BeforeEach
    void setUp() {
        MessageProperties properties = new MessageProperties();
        properties.setMaxTextLength(10);
        hub = new MessageHub(registry, delivery, properties);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("合法私聊按登录主体投递，发起人姓名取服务端登记值")
    void should_publish_chat_with_principal_name() {
        when(registry.userIdOf(SESSION_ID)).thenReturn(FROM_USER_ID);
        when(registry.online(TO_USER_ID)).thenReturn(true);
        when(registry.displayNameOf(SESSION_ID)).thenReturn("张三");

        hub.handle(session, chatFrame("2", "在吗"));

        verify(delivery).publishChat(FROM_USER_ID, "张三", TO_USER_ID, "在吗");
        verify(registry).touch(SESSION_ID);
    }

    @Test
    @DisplayName("目标离线时不投递，回离线错误码")
    void should_reject_offline_target() {
        when(registry.userIdOf(SESSION_ID)).thenReturn(FROM_USER_ID);
        when(registry.online(TO_USER_ID)).thenReturn(false);

        hub.handle(session, chatFrame("2", "在吗"));

        verify(delivery, never()).publishChat(org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), anyString());
        String frame = captureErrorFrame();
        assertTrue(frame.contains("对方不在线"), frame);
        assertTrue(frame.contains("4404"), frame);
    }

    @Test
    @DisplayName("文本超过上限时不投递")
    void should_reject_overlong_text() {
        when(registry.userIdOf(SESSION_ID)).thenReturn(FROM_USER_ID);

        hub.handle(session, chatFrame("2", "01234567890"));

        verify(delivery, never()).publishChat(org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), anyString());
        assertTrue(captureErrorFrame().contains("单条消息不能超过"));
    }

    @Test
    @DisplayName("不能给自己发消息")
    void should_reject_self_chat() {
        when(registry.userIdOf(SESSION_ID)).thenReturn(FROM_USER_ID);

        hub.handle(session, chatFrame("1", "hi"));

        verify(delivery, never()).publishChat(org.mockito.ArgumentMatchers.anyLong(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), anyString());
        assertTrue(captureErrorFrame().contains("不能给自己发消息"));
    }

    @Test
    @DisplayName("心跳原样回 pong")
    void should_reply_pong_on_ping() {
        hub.handle(session, "{\"type\":\"PING\"}");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registry).sendFrame(eq(SESSION_ID), captor.capture());
        assertTrue(captor.getValue().contains("PONG"), captor.getValue());
    }

    @Test
    @DisplayName("非法 JSON 回错误帧而不是抛异常")
    void should_reject_bad_frame() {
        hub.handle(session, "not-a-json");

        assertTrue(captureErrorFrame().contains("4400"));
    }

    @Test
    @DisplayName("缺少 type 的帧回错误帧")
    void should_reject_frame_without_type() {
        hub.handle(session, "{\"text\":\"hi\"}");

        assertTrue(captureErrorFrame().contains("缺少 type"));
    }

    @Test
    @DisplayName("接收人格式不正确时回错误帧")
    void should_reject_invalid_target_id() {
        when(registry.userIdOf(SESSION_ID)).thenReturn(FROM_USER_ID);

        hub.handle(session, chatFrame("abc", "hi"));

        assertTrue(captureErrorFrame().contains("接收人格式不正确"));
    }

    /** 帧用文本块拼，避免转义引号把测试数据读花。 */
    private static String chatFrame(String toUserId, String text) {
        return """
                {"type":"CHAT","toUserId":"%s","text":"%s"}
                """.formatted(toUserId, text);
    }

    private String captureErrorFrame() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(registry).sendFrame(eq(SESSION_ID), captor.capture());
        String frame = captor.getValue();
        assertTrue(frame.contains("\"ERROR\""), frame);
        return frame;
    }
}
