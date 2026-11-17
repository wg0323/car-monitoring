package com.cm.admin.monitor;

import com.cm.admin.config.MonitorProperties;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三条主动断路径各守一条：会话消失、账号被禁用、心跳超时。
 * 这些分支如果被删掉，页面只会表现为「连接莫名其妙掉线」，必须有回归证据。
 */
@ExtendWith(MockitoExtension.class)
class MonitorSessionGuardTest {

    private static final Long USER_ID = 100000000000120201L;

    private static final String WS_SESSION_ID = "ws-1";

    private static final String HTTP_SESSION_ID = "http-1";

    /** 复核间隔取 1ms 再睡一下：让「本轮该复核」成立，又不依赖调度线程。 */
    private static final long DUE_INTERVAL_MILLIS = 1L;

    @Mock
    private SessionRepository<Session> sessionRepository;

    @Mock
    private CmUserMapper userMapper;

    @Mock
    private WebSocketSession session;

    private MonitorSessionRegistry registry;

    private MonitorProperties properties;

    private MonitorSessionGuard guard;

    @BeforeEach
    void setUp() {
        registry = new MonitorSessionRegistry();
        properties = new MonitorProperties();
        guard = new MonitorSessionGuard(registry, sessionRepository, userMapper, properties);
        when(session.getId()).thenReturn(WS_SESSION_ID);
        registry.register(session, USER_ID, HTTP_SESSION_ID);
    }

    @Test
    @DisplayName("会话已消失：断开并清空连接")
    void should_close_when_session_missing() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(sessionRepository.findById(HTTP_SESSION_ID)).thenReturn(null);

        guard.reap();

        verify(session).close(any(CloseStatus.class));
        assertEquals(0, registry.connectionCount());
    }

    @Test
    @DisplayName("账号被禁用：断开连接")
    void should_close_when_account_disabled() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(sessionRepository.findById(HTTP_SESSION_ID)).thenReturn(mock(Session.class));
        when(userMapper.selectById(USER_ID)).thenReturn(user("disabled"));
        properties.getSession().setAccountCheckIntervalMillis(DUE_INTERVAL_MILLIS);
        Thread.sleep(5L);

        guard.reap();

        verify(session).close(any(CloseStatus.class));
        assertEquals(0, registry.connectionCount());
    }

    @Test
    @DisplayName("心跳超时：断开连接")
    void should_close_when_heartbeat_timeout() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(sessionRepository.findById(HTTP_SESSION_ID)).thenReturn(mock(Session.class));
        properties.getSession().setHeartbeatTimeoutMillis(DUE_INTERVAL_MILLIS);
        Thread.sleep(5L);

        guard.reap();

        verify(session).close(any(CloseStatus.class));
        assertEquals(0, registry.connectionCount());
    }

    @Test
    @DisplayName("会话有效且账号正常：不踢连接")
    void should_keep_connection_when_all_good() throws Exception {
        when(sessionRepository.findById(HTTP_SESSION_ID)).thenReturn(mock(Session.class));
        when(userMapper.selectById(USER_ID)).thenReturn(user("normal"));
        properties.getSession().setAccountCheckIntervalMillis(DUE_INTERVAL_MILLIS);
        Thread.sleep(5L);

        guard.reap();

        verify(session, never()).close(any(CloseStatus.class));
        assertEquals(1, registry.connectionCount());
    }

    private CmUser user(String state) {
        CmUser user = new CmUser();
        user.setId(USER_ID);
        user.setUserState(state);
        return user;
    }
}
