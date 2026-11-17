package com.cm.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 已登录但无功能权限或越出数据范围时返回 403 JSON。
 */
@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final int FORBIDDEN_STATUS = HttpServletResponse.SC_FORBIDDEN;

    private static final String BODY = "{\"code\":403,\"message\":\"没有该操作权限\",\"data\":null}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("越权访问被拒绝, uri={}, message={}", request.getRequestURI(), accessDeniedException.getMessage());
        response.setStatus(FORBIDDEN_STATUS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
