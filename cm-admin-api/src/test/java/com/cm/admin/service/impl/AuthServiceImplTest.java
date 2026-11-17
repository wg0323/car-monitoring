package com.cm.admin.service.impl;

import com.cm.admin.security.CmUserDetails;
import com.cm.admin.vo.LoginRequest;
import com.cm.admin.vo.SessionVO;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import com.cm.infrastructure.persistence.row.MenuRow;
import com.cm.infrastructure.service.MenuQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Long USER_ID = 100000000000120201L;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private MenuQueryService menuQueryService;

    @Mock
    private CmUserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(authenticationManager, securityContextRepository,
                menuQueryService, userMapper);
    }

    @Test
    void should_snapshot_permissions_and_filter_menus_on_login() {
        CmUserDetails principal = principal(List.of("vehicle:view"));
        when(authenticationManager.authenticate(any())).thenReturn(authenticated(principal));
        when(menuQueryService.menuTree()).thenReturn(List.of(menu("vehicle:view"), menu("system:view")));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionVO session = authService.login(loginRequest(), request, response);

        assertEquals(List.of("vehicle:view"), session.getPermissions());
        assertEquals(1, session.getMenus().size());
        assertEquals("vehicle:view", session.getMenus().get(0).getPermissionCode());
        verify(securityContextRepository).saveContext(any(), eq(request), eq(response));
    }

    @Test
    void should_write_session_context_on_success() {
        CmUserDetails principal = principal(List.of("vehicle:view"));
        when(authenticationManager.authenticate(any())).thenReturn(authenticated(principal));
        when(menuQueryService.menuTree()).thenReturn(List.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        authService.login(loginRequest(), request, response);

        verify(securityContextRepository).saveContext(any(), eq(request), eq(response));
    }

    @Test
    void should_rotate_session_id_when_session_already_exists() {
        CmUserDetails principal = principal(List.of("vehicle:view"));
        when(authenticationManager.authenticate(any())).thenReturn(authenticated(principal));
        when(menuQueryService.menuTree()).thenReturn(List.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getSession(false)).thenReturn(mock(HttpSession.class));

        authService.login(loginRequest(), request, response);

        verify(request).changeSessionId();
    }

    @Test
    void should_propagate_authentication_failure_without_writing_session() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest(), request, response));
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    void should_persist_permissions_snapshot_from_authentication() {
        CmUserDetails principal = principal(List.of("alarm:view", "vehicle:view"));
        when(authenticationManager.authenticate(any())).thenReturn(authenticated(principal));
        when(menuQueryService.menuTree()).thenReturn(List.of(menu("alarm:view"), menu("vehicle:view")));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionVO session = authService.login(loginRequest(), request, response);

        assertEquals(List.of("alarm:view", "vehicle:view"), session.getPermissions());
        assertEquals(2, session.getMenus().size());
    }

    @Test
    void should_invalidate_session_on_logout() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        authService.logout(request);

        verify(session).invalidate();
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("op1");
        request.setPassword("123321");
        return request;
    }

    private CmUserDetails principal(List<String> permissionCodes) {
        return new CmUserDetails(USER_ID, "op1", "hash", "监控员一", false, true, permissionCodes);
    }

    private Authentication authenticated(CmUserDetails principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private MenuRow menu(String permissionCode) {
        MenuRow row = new MenuRow();
        row.setPermissionCode(permissionCode);
        row.setName(permissionCode);
        row.setIcon(permissionCode);
        row.setMenuSort(10);
        row.setUrl("/" + permissionCode);
        return row;
    }
}
