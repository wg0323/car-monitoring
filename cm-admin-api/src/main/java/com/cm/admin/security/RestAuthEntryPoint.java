package com.cm.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证访问受保护接口时返回 401 JSON。
 * 手写固定报文而不注入 ObjectMapper：Boot 4 的 Jackson 3 与 Knife4j 带入的 Jackson 2 并存，
 * 这里只有一个固定文案，手写可避免序列化器选错。
 */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private static final int UNAUTHORIZED_STATUS = HttpServletResponse.SC_UNAUTHORIZED;

    private static final String BODY = "{\"code\":401,\"message\":\"未登录或会话已过期\",\"data\":null}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(UNAUTHORIZED_STATUS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
