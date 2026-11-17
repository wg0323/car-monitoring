package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.admin.config.MonitorProperties;
import com.cm.admin.common.PageResult;
import com.cm.admin.monitor.OnlineVehicleResolver;
import com.cm.admin.vo.MonitorSummaryVO;
import com.cm.admin.vo.MonitorVehicleVO;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.monitor.PositionSnapshot;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorAppServiceImplTest {

    private static final Long OPERATOR_ID = 100000000000120201L;

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final String SIM_NO = "13800138000";

    @Mock
    private CmVehicleService vehicleService;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private MonitorPositionStore positionStore;

    @Mock
    private OnlineVehicleResolver onlineVehicleResolver;

    private MonitorProperties properties;

    private MonitorAppServiceImpl service;

    private DataScope operatorScope;

    @BeforeEach
    void setUp() {
        properties = new MonitorProperties();
        service = new MonitorAppServiceImpl(vehicleService, dataScopeService, positionStore, properties,
                onlineVehicleResolver);
        operatorScope = new DataScope(OPERATOR_ID, false, List.of(1L), List.of(DALIAN_DEPARTMENT_ID));
    }

    @Test
    @DisplayName("部门筛选值越界直接拒绝，不返回空列表")
    void should_reject_department_outside_scope() {
        when(dataScopeService.resolve(OPERATOR_ID)).thenReturn(operatorScope);

        assertThrows(AccessDeniedException.class,
                () -> service.page(OPERATOR_ID, 1, 20, null, HUNAN_DEPARTMENT_ID, null));
    }

    @Test
    @DisplayName("列表行补上最新位置与在线态")
    void should_enrich_rows_with_position_and_online() {
        stubScope();
        stubPage(vehicleRow(10L, SIM_NO));
        when(positionStore.latestAll(List.of(SIM_NO)))
                .thenReturn(Map.of(SIM_NO, new PositionSnapshot(30000000, 120000000, 603, 90, 0, 1,
                        "2026-11-15T04:00:00Z")));
        when(positionStore.onlineAll(List.of(SIM_NO))).thenReturn(Map.of(SIM_NO, true));

        PageResult<MonitorVehicleVO> result = service.page(OPERATOR_ID, 1, 20, null, null, null);

        MonitorVehicleVO row = result.getRecords().get(0);
        assertEquals(30.0, row.getLatitude());
        assertEquals(60.3, row.getSpeedKph());
        assertTrue(row.isOnline());
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("没有位置的车不给 0 度，避免被画到赤道")
    void should_leave_position_null_when_missing() {
        stubScope();
        stubPage(vehicleRow(10L, SIM_NO));
        when(positionStore.latestAll(List.of(SIM_NO))).thenReturn(Map.of());
        when(positionStore.onlineAll(List.of(SIM_NO))).thenReturn(Map.of(SIM_NO, false));

        MonitorVehicleVO row = service.page(OPERATOR_ID, 1, 20, null, null, null).getRecords().get(0);

        assertNull(row.getLatitude());
        assertNull(row.getLongitude());
        assertNull(row.getSpeedKph());
        assertFalse(row.isOnline());
    }

    @Test
    @DisplayName("非超管只统计落在数据范围内的在线车")
    void should_count_online_within_scope() {
        stubScope();
        when(vehicleService.countByScope(operatorScope)).thenReturn(100L);
        when(positionStore.onlineSimNos()).thenReturn(Set.of("sim-a", "sim-b", "sim-c"));
        when(vehicleService.onlineSimNosInScope(operatorScope, Set.of("sim-a", "sim-b", "sim-c")))
                .thenReturn(List.of("sim-a", "sim-b"));
        when(positionStore.parkingCount(List.of("sim-a", "sim-b"))).thenReturn(1);

        MonitorSummaryVO summary = service.summary(OPERATOR_ID);

        assertEquals(100, summary.getTotal());
        assertEquals(2, summary.getOnline());
        assertEquals(98, summary.getOffline());
        assertEquals(1, summary.getParking());
        assertEquals(2.0, summary.getOnlineRate());
    }

    @Test
    @DisplayName("超管的在线数也要与车辆表求交，租约孤儿不会把上线率抬过 100%")
    void should_intersect_lease_set_with_vehicles_for_super_admin() {
        DataScope superScope = DataScope.superAdmin(OPERATOR_ID);
        when(dataScopeService.resolve(OPERATOR_ID)).thenReturn(superScope);
        when(vehicleService.countByScope(superScope)).thenReturn(3L);
        when(positionStore.onlineSimNos()).thenReturn(Set.of("sim-a", "sim-b", "sim-orphan"));
        when(vehicleService.onlineSimNosInScope(superScope, Set.of("sim-a", "sim-b", "sim-orphan")))
                .thenReturn(List.of("sim-a", "sim-b"));
        when(positionStore.parkingCount(List.of("sim-a", "sim-b"))).thenReturn(0);

        MonitorSummaryVO summary = service.summary(OPERATOR_ID);

        assertEquals(2, summary.getOnline());
        assertEquals(1, summary.getOffline());
        assertEquals(66.67, summary.getOnlineRate());
    }

    @Test
    @DisplayName("状态栏在缓存窗口内不重复统计")
    void should_cache_summary() {
        stubScope();
        when(vehicleService.countByScope(operatorScope)).thenReturn(10L);
        when(positionStore.onlineSimNos()).thenReturn(Set.of());

        service.summary(OPERATOR_ID);
        service.summary(OPERATOR_ID);

        verify(vehicleService, times(1)).countByScope(operatorScope);
    }

    @Test
    @DisplayName("订阅校验只回数据范围内的车辆")
    void should_filter_subscription_by_scope() {
        stubScope();
        when(vehicleService.listInScopeByIds(eq(operatorScope), anyCollection()))
                .thenReturn(List.of(vehicleRow(10L, SIM_NO)));

        Map<Long, String> allowed = service.vehiclesInScope(OPERATOR_ID, List.of(10L, 99L));

        assertEquals(Map.of(10L, SIM_NO), allowed);
    }

    private void stubScope() {
        when(dataScopeService.resolve(OPERATOR_ID)).thenReturn(operatorScope);
    }

    private void stubPage(VehicleRow row) {
        IPage<VehicleRow> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(row));
        when(vehicleService.pageByScope(eq(operatorScope), anyLong(), anyLong(), isNull(), isNull(), isNull(),
                isNull(), any())).thenReturn(page);
    }

    private VehicleRow vehicleRow(Long id, String simNo) {
        VehicleRow row = new VehicleRow();
        row.setId(id);
        row.setPlateNo("辽B00001");
        row.setSimNo(simNo);
        row.setDepartmentName("大连中心");
        return row;
    }
}
