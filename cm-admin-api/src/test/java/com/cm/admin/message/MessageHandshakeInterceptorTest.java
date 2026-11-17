package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import com.cm.admin.security.CmUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 握手鉴权覆盖：未登录 401、缺 message:view 403、有权限放行并把登录主体写进连接属性、
 * Origin 不在白名单时 403。规则与实时监控握手同一套。
 */
class MessageHandshakeInterceptorTest {

    private static final long USER_ID = 100L;

    private final MessageHandshakeInterceptor interceptor =
            new MessageHandshakeInterceptor(new MessageProperties());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("未认证的握手返回 401")
    void should_reject_unauthenticated() {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>());

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    @Test
    @DisplayName("缺少 message:view 的账号返回 403")
    void should_reject_without_permission() {
        authenticate(List.of("monitor:view"));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>());

        assertFalse(allowed);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    @Test
    @DisplayName("有权限的握手写入用户 ID、姓名与 HTTP 会话 ID")
    void should_accept_with_permission() {
        authenticate(List.of("message:view"));
        MockHttpServletRequest request = request();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, attributes);

        assertTrue(allowed);
        assertEquals(USER_ID, attributes.get(MessageHandshakeInterceptor.ATTR_USER_ID));
        assertEquals("监控员一", attributes.get(MessageHandshakeInterceptor.ATTR_DISPLAY_NAME));
        assertTrue(attributes.get(MessageHandshakeInterceptor.ATTR_HTTP_SESSION_ID) instanceof String sessionId
                && !sessionId.isBlank());
    }

    @Test
    @DisplayName("跨源握手被拒，避免任意站点借用户 Cookie 建连")
    void should_reject_foreign_origin() {
        authenticate(List.of("message:view"));
        MockHttpServletRequest request = request();
        request.addHeader("Origin", "http://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response), null, new HashMap<>());

        assertFalse(allowed);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    private void authenticate(List<String> permissionCodes) {
        CmUserDetails principal = new CmUserDetails(USER_ID, "op1", "ignored", "监控员一", false, true,
                permissionCodes);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        principal.getAuthorities()));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8081);
        return request;
    }
}
