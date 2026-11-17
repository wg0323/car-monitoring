package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.AuthService;
import com.cm.admin.vo.CsrfTokenVO;
import com.cm.admin.vo.LoginRequest;
import com.cm.admin.vo.SessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "登录、会话、注销与 CSRF 令牌")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "预热 CSRF 令牌",
            description = "调用一次后服务端会把令牌写进 XSRF-TOKEN cookie；前端从 cookie 取值放进请求头 X-XSRF-TOKEN")
    @ApiResponse(responseCode = "200", description = "返回请求头与参数名称，令牌值已在响应 cookie 中")
    @GetMapping("/csrf")
    public ApiResult<CsrfTokenVO> csrf(CsrfToken csrfToken) {
        return ApiResult.ok(new CsrfTokenVO(csrfToken.getHeaderName(), csrfToken.getParameterName()));
    }

    @Operation(summary = "登录", description = "口令错误或账号停用返回 401；成功返回用户、权限码快照与可见菜单")
    @ApiResponse(responseCode = "200", description = "登录成功，会话写入 Redis")
    @ApiResponse(responseCode = "401", description = "账号或口令错误，或账号已停用")
    @PostMapping("/login")
    public ApiResult<SessionVO> login(@Valid @RequestBody LoginRequest request,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        return ApiResult.ok(authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "当前会话", description = "返回登录用户、权限码快照与按权限过滤后的侧边菜单")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @GetMapping("/me")
    public ApiResult<SessionVO> me(@AuthenticationPrincipal CmUserDetails principal) {
        return ApiResult.ok(authService.currentSession(principal));
    }

    @Operation(summary = "注销", description = "清空安全上下文并让会话失效")
    @ApiResponse(responseCode = "200", description = "注销成功")
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ApiResult.ok(null);
    }
}
