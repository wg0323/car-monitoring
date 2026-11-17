package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cm.admin.converter.ViewConverter;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.AuthService;
import com.cm.admin.vo.LoginRequest;
import com.cm.admin.vo.MenuVO;
import com.cm.admin.vo.SessionVO;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import com.cm.infrastructure.service.MenuQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录与会话实现。口令比较由 DaoAuthenticationProvider + BCrypt 完成，本类只管会话与快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;

    private final SecurityContextRepository securityContextRepository;

    private final MenuQueryService menuQueryService;

    private final CmUserMapper userMapper;

    @Override
    public SessionVO login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(), request.getPassword()));
        CmUserDetails principal = (CmUserDetails) authentication.getPrincipal();

        // 会话固定攻击防护：登录前已存在会话时换新 ID，再把安全上下文写进 Redis 会话。
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        touchLastLogin(principal.getUserId());
        log.info("登录成功, userId={}, username={}, superAdmin={}",
                principal.getUserId(), principal.getUsername(), principal.isSuperAdmin());
        return buildSession(principal);
    }

    @Override
    public SessionVO currentSession(CmUserDetails principal) {
        return buildSession(principal);
    }

    @Override
    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        log.info("注销会话完成");
    }

    private SessionVO buildSession(CmUserDetails principal) {
        List<String> permissions = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        Set<String> granted = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        List<MenuVO> menus = menuQueryService.menuTree().stream()
                .filter(row -> granted.contains(row.getPermissionCode()))
                .map(ViewConverter::toMenuVO)
                .toList();
        return new SessionVO(ViewConverter.toLoginUserVO(principal), permissions, menus);
    }

    /**
     * 最后登录时间属于审计信息，写失败不应把已经建立的会话判为登录失败。
     */
    private void touchLastLogin(Long userId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            userMapper.update(null, new LambdaUpdateWrapper<CmUser>()
                    .eq(CmUser::getId, userId)
                    .set(CmUser::getLastLoginTime, now)
                    .set(CmUser::getUpdateTime, now));
        } catch (RuntimeException ex) {
            log.warn("更新最后登录时间失败, userId={}", userId, ex);
        }
    }
}
