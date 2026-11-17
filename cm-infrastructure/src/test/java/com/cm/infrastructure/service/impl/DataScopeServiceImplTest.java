package com.cm.infrastructure.service.impl;

import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.entity.CmUserDepartment;
import com.cm.infrastructure.persistence.mapper.CmUserDepartmentMapper;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private CmUserMapper userMapper;

    @Mock
    private CmUserDepartmentMapper userDepartmentMapper;

    @InjectMocks
    private DataScopeServiceImpl dataScopeService;

    @Test
    void should_return_unfiltered_scope_when_user_type_is_admin() {
        CmUser admin = user("admin", 0L, "enabled");
        when(userMapper.selectById(USER_ID)).thenReturn(admin);

        DataScope scope = dataScopeService.resolve(USER_ID);

        assertTrue(scope.isSuperAdmin());
        assertFalse(scope.hasDepartmentFilter());
        verify(userDepartmentMapper, never()).selectList(any());
    }

    @Test
    void should_treat_uppercase_admin_as_super_admin() {
        CmUser admin = user(" ADMIN ", 0L, "enabled");
        when(userMapper.selectById(USER_ID)).thenReturn(admin);

        assertTrue(dataScopeService.resolve(USER_ID).isSuperAdmin());
        verify(userDepartmentMapper, never()).selectList(any());
    }

    @Test
    void should_use_only_server_side_departments_when_user_is_not_admin() {
        CmUser user = user("operation", 117440527L, "enabled");
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userDepartmentMapper.selectList(any())).thenReturn(List.of(relation(3L), relation(3L), relation(4L)));

        DataScope scope = dataScopeService.resolve(USER_ID);

        assertFalse(scope.isSuperAdmin());
        assertEquals(List.of(3L, 4L), scope.getDepartmentIds());
        assertEquals(List.of(117440527L), scope.getCompanyIds());
    }

    @Test
    void should_reject_when_account_is_disabled() {
        CmUser user = user("admin", 0L, "disabled");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        assertThrows(CmBusinessException.class, () -> dataScopeService.resolve(USER_ID));
        verify(userDepartmentMapper, never()).selectList(any());
    }

    @Test
    void should_reject_when_company_id_is_missing() {
        CmUser user = user("monitor", null, "enabled");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        assertThrows(CmBusinessException.class, () -> dataScopeService.resolve(USER_ID));
    }

    @Test
    void should_reject_when_no_department_is_assigned() {
        CmUser user = user("monitor", 9L, "enabled");
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userDepartmentMapper.selectList(any())).thenReturn(List.of());

        assertThrows(CmBusinessException.class, () -> dataScopeService.resolve(USER_ID));
    }

    @Test
    void should_reject_when_user_does_not_exist() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThrows(CmBusinessException.class, () -> dataScopeService.resolve(USER_ID));
    }

    @Test
    void should_reject_when_user_id_is_null() {
        assertThrows(CmBusinessException.class, () -> dataScopeService.resolve(null));
    }

    private CmUser user(String userType, Long companyId, String userState) {
        CmUser user = new CmUser();
        user.setUserType(userType);
        user.setCompanyId(companyId);
        user.setUserState(userState);
        return user;
    }

    private CmUserDepartment relation(Long departmentId) {
        CmUserDepartment relation = new CmUserDepartment();
        relation.setUserId(USER_ID);
        relation.setDepartmentId(departmentId);
        return relation;
    }
}
