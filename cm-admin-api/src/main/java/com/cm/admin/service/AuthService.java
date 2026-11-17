package com.cm.admin.service;

import com.cm.admin.security.CmUserDetails;
import com.cm.admin.vo.LoginRequest;
import com.cm.admin.vo.SessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录与会话。认证交给 AuthenticationManager，本服务只负责会话落库、权限快照与登录时间。
 */
public interface AuthService {

    /**
     * 校验口令并建立会话；失败抛 AuthenticationException，由全局异常处理转 401。
     */
    SessionVO login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * 当前会话的用户、权限码与可见菜单。权限码来自登录时的快照，菜单按权限码过滤后下发。
     */
    SessionVO currentSession(CmUserDetails principal);

    /**
     * 注销：清空安全上下文并让会话失效，Redis 中的 cm:session 记录随之删除。
     */
    void logout(HttpServletRequest httpRequest);
}
