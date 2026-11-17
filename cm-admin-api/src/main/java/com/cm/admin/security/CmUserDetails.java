package com.cm.admin.security;

import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 认证主体。权限码在登录时算好放进 authorities，随会话序列化到 Redis，
 * 后续请求的 @PreAuthorize 与 /auth/me 都读同一份快照，不再逐请求查库。
 */
@Getter
@ToString(exclude = "password")
public class CmUserDetails implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;

    private final String username;

    private final String password;

    private final String displayName;

    private final boolean superAdmin;

    private final boolean enabled;

    private final List<GrantedAuthority> authorities;

    public CmUserDetails(Long userId, String username, String password, String displayName,
                         boolean superAdmin, boolean enabled, List<String> permissionCodes) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.superAdmin = superAdmin;
        this.enabled = enabled;
        this.authorities = new ArrayList<>(permissionCodes.size());
        for (String code : permissionCodes) {
            this.authorities.add(new SimpleGrantedAuthority(code));
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
