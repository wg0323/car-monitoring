package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import com.cm.admin.security.CmUserDetails;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 消息中心握手鉴权：与会话必须已认证，且拥有 message:view 才允许连。
 * 规则与实时监控握手一致（Origin 同源或白名单），这是第二处 WS 端点，
 * 按开发三步法暂不抽公共件，第三处出现再统一。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandshakeInterceptor implements HandshakeInterceptor {

    /** 连接属性里的登录主体 ID，私聊发起方只认这个值，不信客户端自称。 */
    static final String ATTR_USER_ID = "cm.message.userId";

    static final String ATTR_DISPLAY_NAME = "cm.message.displayName";

    /**
     * 连接属性里的 HTTP 会话 ID。必须与 WebSocket 自己的 sessionId 分开存：
     * WebSocketSession#getId 是连接的随机 ID，拿它去 SessionRepository 查永远是 null。
     */
    static final String ATTR_HTTP_SESSION_ID = "cm.message.httpSessionId";

    private static final String MESSAGE_VIEW = "message:view";

    private static final int DEFAULT_HTTP_PORT = 80;

    private static final int DEFAULT_HTTPS_PORT = 443;

    private final MessageProperties properties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!originAllowed(request)) {
            log.warn("消息中心握手拒绝：Origin 不在允许范围, origin={}", request.getHeaders().getOrigin());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        Principal principal = request.getPrincipal();
        Authentication authentication = principal instanceof Authentication candidate
                ? candidate : SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CmUserDetails user)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> MESSAGE_VIEW.equals(authority.getAuthority()))) {
            log.warn("消息中心握手拒绝：缺少 {} 权限, userId={}", MESSAGE_VIEW, user.getUserId());
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATTR_USER_ID, user.getUserId());
        attributes.put(ATTR_DISPLAY_NAME, user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
        attributes.put(ATTR_HTTP_SESSION_ID, httpSessionId(request));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.debug("消息中心握手异常", exception);
        }
    }

    /** 握手时的 HTTP 会话 ID。取不到就留空，守卫会当即把连接踢掉，不会让它裸跑。 */
    private String httpSessionId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            return session == null ? "" : session.getId();
        }
        return "";
    }

    /**
     * 白名单优先；没配白名单时只允许同源。没有 Origin 的请求来自运维脚本与集成测试，放行给认证关卡。
     */
    private boolean originAllowed(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return true;
        }
        List<String> allowedOrigins = properties.getAllowedOrigins();
        if (!allowedOrigins.isEmpty()) {
            return allowedOrigins.stream().anyMatch(item -> item.equalsIgnoreCase(origin));
        }
        return sameOrigin(request, origin);
    }

    private boolean sameOrigin(ServerHttpRequest request, String origin) {
        HttpHeaders headers = request.getHeaders();
        if (headers.getHost() == null) {
            return false;
        }
        try {
            URI originUri = URI.create(origin);
            String originHost = originUri.getHost();
            int originPort = effectivePort(originUri.getScheme(), originUri.getPort());
            String requestHost = headers.getHost().getHostName();
            int requestPort = effectivePort(request.getURI().getScheme(), headers.getHost().getPort());
            return originHost != null && originHost.equalsIgnoreCase(requestHost) && originPort == requestPort;
        } catch (IllegalArgumentException ex) {
            log.warn("Origin 解析失败, origin={}", origin);
            return false;
        }
    }

    private int effectivePort(String scheme, int port) {
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)
                ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
    }
}
