package com.cm.admin.service.impl;

import com.cm.admin.vo.DepartmentDetailVO;
import com.cm.admin.vo.DepartmentSaveRequest;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.exception.CmConflictException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmDepartment;
import com.cm.infrastructure.persistence.row.DepartmentDetailRow;
import com.cm.infrastructure.service.CmDepartmentService;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.service.ReportQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentAppServiceImplTest {

    private static final long USER_ID = 100000000000120201L;

    private static final long DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final long NEW_DEPARTMENT_ID = 100000000000000999L;

    @Mock
    private ReportQueryService reportQueryService;

    @Mock
    private CmDepartmentService departmentService;

    @Mock
    private DataScopeService dataScopeService;

    private DepartmentAppServiceImpl service;

    private DataScope scope;

    @BeforeEach
    void setUp() {
        service = new DepartmentAppServiceImpl(reportQueryService, departmentService, dataScopeService);
        scope = new DataScope(USER_ID, false, List.of(1L), List.of(DEPARTMENT_ID));
    }

    @Test
    @DisplayName("名称重复时拒绝新增，不落库也不授权")
    void should_reject_duplicated_name() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.existsName("重复车组", null)).thenReturn(true);

        assertThrows(CmBusinessException.class, () -> service.create(USER_ID, request("重复车组", 0L)));
        verify(departmentService, never()).save(any());
        verify(departmentService, never()).grantToUser(USER_ID, NEW_DEPARTMENT_ID);
    }

    @Test
    @DisplayName("上级车组越界时拒绝新增")
    void should_reject_parent_outside_scope() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.existsName("新分组", null)).thenReturn(false);
        when(departmentService.detailInScope(scope, HUNAN_DEPARTMENT_ID)).thenReturn(null);

        assertThrows(AccessDeniedException.class,
                () -> service.create(USER_ID, request("新分组", HUNAN_DEPARTMENT_ID)));
        verify(departmentService, never()).save(any());
    }

    @Test
    @DisplayName("新增成功后自动授权给创建人并回读新分组")
    void should_grant_new_department_to_creator() {
        DataScope refreshed = new DataScope(USER_ID, false, List.of(1L),
                List.of(DEPARTMENT_ID, NEW_DEPARTMENT_ID));
        DepartmentDetailRow created = new DepartmentDetailRow();
        created.setId(NEW_DEPARTMENT_ID);
        created.setName("新分组");
        created.setParentId(0L);
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope, refreshed);
        when(departmentService.existsName("新分组", null)).thenReturn(false);
        when(departmentService.save(any())).thenAnswer(invocation -> {
            CmDepartment entity = invocation.getArgument(0);
            entity.setId(NEW_DEPARTMENT_ID);
            return true;
        });
        when(departmentService.detailInScope(refreshed, NEW_DEPARTMENT_ID)).thenReturn(created);

        DepartmentDetailVO vo = service.create(USER_ID, request("新分组", 0L));

        assertEquals(NEW_DEPARTMENT_ID, vo.getId());
        verify(departmentService).grantToUser(USER_ID, NEW_DEPARTMENT_ID);
    }

    @Test
    @DisplayName("上级不能是本车组")
    void should_reject_self_parent() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.detailInScope(scope, DEPARTMENT_ID)).thenReturn(existing());
        when(departmentService.existsName("大连中心", DEPARTMENT_ID)).thenReturn(false);

        assertThrows(CmBusinessException.class,
                () -> service.update(USER_ID, DEPARTMENT_ID, request("大连中心", DEPARTMENT_ID)));
        verify(departmentService, never()).updateById(any());
    }

    @Test
    @DisplayName("上级不能是自己的下级")
    void should_reject_descendant_as_parent() {
        long childId = 100000000117440546L;
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.detailInScope(scope, DEPARTMENT_ID)).thenReturn(existing());
        when(departmentService.existsName("大连中心", DEPARTMENT_ID)).thenReturn(false);
        when(departmentService.descendantIds(DEPARTMENT_ID)).thenReturn(List.of(childId));

        assertThrows(CmBusinessException.class,
                () -> service.update(USER_ID, DEPARTMENT_ID, request("大连中心", childId)));
        verify(departmentService, never()).updateById(any());
    }

    @Test
    @DisplayName("修改时保留原企业 ID，名称与上级正常落库")
    void should_keep_company_id_on_update() {
        DepartmentDetailRow existing = existing();
        existing.setCompanyId(7L);
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.detailInScope(scope, DEPARTMENT_ID)).thenReturn(existing);
        when(departmentService.existsName("大连中心", DEPARTMENT_ID)).thenReturn(false);

        service.update(USER_ID, DEPARTMENT_ID, request("大连中心", 0L));

        ArgumentCaptor<CmDepartment> captor = ArgumentCaptor.forClass(CmDepartment.class);
        verify(departmentService).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getCompanyId());
        assertEquals("大连中心", captor.getValue().getName());
    }

    @Test
    @DisplayName("有车辆或有子组的分组不能删除")
    void should_reject_delete_when_in_use() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.detailInScope(scope, DEPARTMENT_ID)).thenReturn(existing());
        when(departmentService.countVehicles(DEPARTMENT_ID)).thenReturn(2L);

        assertThrows(CmConflictException.class, () -> service.delete(USER_ID, DEPARTMENT_ID));
        verify(departmentService, never()).removeById(DEPARTMENT_ID);
    }

    @Test
    @DisplayName("空分组按逻辑删除，调用 MyBatis-Plus 的 removeById")
    void should_logically_delete_empty_department() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(departmentService.detailInScope(scope, DEPARTMENT_ID)).thenReturn(existing());
        when(departmentService.countVehicles(DEPARTMENT_ID)).thenReturn(0L);
        when(departmentService.countChildren(DEPARTMENT_ID)).thenReturn(0L);

        service.delete(USER_ID, DEPARTMENT_ID);

        verify(departmentService).removeById(DEPARTMENT_ID);
    }

    private DepartmentSaveRequest request(String name, Long parentId) {
        DepartmentSaveRequest request = new DepartmentSaveRequest();
        request.setName(name);
        request.setParentId(parentId);
        return request;
    }

    private DepartmentDetailRow existing() {
        DepartmentDetailRow row = new DepartmentDetailRow();
        row.setId(DEPARTMENT_ID);
        row.setName("大连中心");
        row.setParentId(0L);
        return row;
    }
}
