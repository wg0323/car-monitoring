package com.cm.domain.scope;

import com.cm.domain.exception.CmBusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataScopeTest {

    @Test
    void should_skip_filter_when_user_is_super_admin() {
        DataScope scope = DataScope.superAdmin(1L);

        assertTrue(scope.isSuperAdmin());
        assertFalse(scope.hasDepartmentFilter());
        assertTrue(scope.getDepartmentIds().isEmpty());
        assertTrue(scope.getCompanyIds().isEmpty());
    }

    @Test
    void should_keep_department_filter_when_user_is_not_admin() {
        DataScope scope = new DataScope(2L, false, List.of(9L), List.of(3L, 4L));

        assertFalse(scope.isSuperAdmin());
        assertTrue(scope.hasDepartmentFilter());
        assertEquals(List.of(3L, 4L), scope.getDepartmentIds());
    }

    @Test
    void should_reject_when_non_admin_has_no_department() {
        assertThrows(CmBusinessException.class,
                () -> new DataScope(2L, false, List.of(9L), List.of()));
    }

    @Test
    void should_reject_when_non_admin_has_no_company() {
        assertThrows(CmBusinessException.class,
                () -> new DataScope(2L, false, List.of(), List.of(3L)));
    }

    @Test
    void should_reject_when_scope_exceeds_limit() {
        List<Long> tooMany = LongStream.rangeClosed(0L, DataScope.MAX_SCOPE_IDS)
                .boxed()
                .toList();

        assertThrows(CmBusinessException.class,
                () -> new DataScope(2L, false, List.of(9L), tooMany));
    }

    @Test
    void should_make_id_lists_immutable() {
        DataScope scope = new DataScope(2L, false, List.of(9L), List.of(3L));

        assertThrows(UnsupportedOperationException.class, () -> scope.getDepartmentIds().add(4L));
        assertThrows(UnsupportedOperationException.class, () -> scope.getCompanyIds().add(10L));
    }

    @Test
    void should_defensively_copy_mutable_input() {
        List<Long> departments = new ArrayList<>(List.of(3L));
        DataScope scope = new DataScope(2L, false, List.of(9L), departments);

        departments.add(99L);

        assertEquals(List.of(3L), scope.getDepartmentIds());
    }

    @Test
    void should_only_treat_admin_user_type_as_super_admin() {
        assertTrue(DataScope.isAdminUserType("admin"));
        assertTrue(DataScope.isAdminUserType(" ADMIN "));
        assertFalse(DataScope.isAdminUserType("operation"));
        assertFalse(DataScope.isAdminUserType("monitor"));
        assertFalse(DataScope.isAdminUserType(null));
    }
}
