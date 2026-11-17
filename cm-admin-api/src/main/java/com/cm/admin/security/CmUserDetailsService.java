package com.cm.admin.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import com.cm.infrastructure.service.MenuQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按登录名装载认证主体。账号停用走 UserDetails.isEnabled()，口令比较交给 DaoAuthenticationProvider 与 BCrypt。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmUserDetailsService implements UserDetailsService {

    private static final String DISABLED_STATE = "disabled";

    private final CmUserMapper userMapper;

    private final MenuQueryService menuQueryService;

    @Override
    public CmUserDetails loadUserByUsername(String username) {
        CmUser user = userMapper.selectOne(new LambdaQueryWrapper<CmUser>()
                .eq(CmUser::getUsername, username)
                .eq(CmUser::getDeleted, false));
        if (user == null) {
            throw new UsernameNotFoundException("账号不存在");
        }
        boolean superAdmin = DataScope.isAdminUserType(user.getUserType());
        List<String> permissionCodes = menuQueryService.permissionCodes(user.getId(), superAdmin);
        boolean enabled = !DISABLED_STATE.equalsIgnoreCase(user.getUserState());
        log.debug("装载认证主体, userId={}, superAdmin={}, permissionCount={}",
                user.getId(), superAdmin, permissionCodes.size());
        return new CmUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getDisplayName(), superAdmin, enabled, permissionCodes);
    }
}
