package com.cm.admin.monitor;

import com.cm.admin.config.MonitorProperties;
import com.cm.admin.security.CmUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorHandshakeInterceptorTest {

    private static final Long USER_ID = 100000000000120201L;

    private static final int UNAUTHORIZED = 401;

    private static final int FORBIDDEN = 403;

    private MonitorProperties properties;

    private MonitorHandshakeInterceptor interceptor;

    private MockHttpServletRequest servletRequest;

    private MockHttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        properties = new MonitorProperties();
        interceptor = new MonitorHandshakeInterceptor(properties);
        servletRequest = new MockHttpServletRequest("GET", "/ws/monitor");
        servletRequest.setScheme("http");
        servletRequest.setServerName("localhost");
        servletRequest.setServerPort(5173);
        servletRequest.addHeader("Host", "localhost:5173");
        servletResponse = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("同源且已认证：握手放行并把主体 ID 写进连接属性")
    void should_allow_same_origin_authenticated() {
        authenticate();
        var attributes = new HashMap<String, Object>();

        assertTrue(handshake("http://localhost:5173", attributes));
        assertEquals(USER_ID, attributes.get(MonitorHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    @DisplayName("未认证：握手拒绝并回 401")
    void should_reject_unauthenticated() {
        var attributes = new HashMap<String, Object>();

        assertFalse(handshake("http://localhost:5173", attributes));
        assertEquals(UNAUTHORIZED, servletResponse.getStatus());
        assertNull(attributes.get(MonitorHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    @DisplayName("跨站 Origin：握手拒绝并回 403")
    void should_reject_cross_origin() {
        authenticate();

        assertFalse(handshake("http://evil.example.com:5173", new HashMap<>()));
        assertEquals(FORBIDDEN, servletResponse.getStatus());
    }

    @Test
    @DisplayName("没有 Origin 的客户端（运维脚本与集成测试）放给认证关卡")
    void should_allow_request_without_origin() {
        authenticate();

        assertTrue(handshake(null, new HashMap<>()));
    }

    @Test
    @DisplayName("配了白名单就按白名单放行")
    void should_allow_whitelisted_origin() {
        properties.setAllowedOrigins(List.of("https://cm.example.com"));
        authenticate();

        assertTrue(handshake("https://cm.example.com", new HashMap<>()));
    }

    private boolean handshake(String origin, Map<String, Object> attributes) {
        if (origin != null) {
            servletRequest.addHeader("Origin", origin);
        }
        return interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse), null, attributes);
    }

    private void authenticate() {
        CmUserDetails details = new CmUserDetails(USER_ID, "op1", "n/a", "监控员一", false, true,
                List.of("monitor:view"));
        servletRequest.setUserPrincipal(new UsernamePasswordAuthenticationToken(details, null,
                details.getAuthorities()));
    }
}
