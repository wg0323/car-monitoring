package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.admin.vo.CommandVehicleVO;
import com.cm.domain.exception.CmConflictException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmTerminalCommandService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandAppServiceImplTest {

    private static final long USER_ID = 1L;

    private static final long VEHICLE_ID = 42L;

    private static final String SIM_NO = "15880022239";

    private static final DataScope SCOPE = new DataScope(USER_ID, false, List.of(9L), List.of(99L));

    @Mock
    private CmTerminalCommandService terminalCommandService;

    @Mock
    private CmVehicleService vehicleService;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private MonitorPositionStore positionStore;

    private CommandAppServiceImpl commandAppService;

    @BeforeEach
    void setUp() {
        commandAppService = new CommandAppServiceImpl(terminalCommandService, vehicleService,
                dataScopeService, positionStore);
    }

    @Test
    @DisplayName("车辆不在数据范围内时拒绝下发")
    void should_reject_when_vehicle_out_of_scope() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(vehicleService.listInScopeByIds(SCOPE, List.of(VEHICLE_ID))).thenReturn(List.of());

        assertThrows(AccessDeniedException.class,
                () -> commandAppService.sendPositionQuery(USER_ID, VEHICLE_ID));
        verify(terminalCommandService, never()).accept(any(), any());
    }

    @Test
    @DisplayName("终端离线时返回冲突，不落命令")
    void should_reject_when_vehicle_offline() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(vehicleService.listInScopeByIds(SCOPE, List.of(VEHICLE_ID))).thenReturn(List.of(vehicle()));
        when(positionStore.online(SIM_NO)).thenReturn(false);

        assertThrows(CmConflictException.class,
                () -> commandAppService.sendPositionQuery(USER_ID, VEHICLE_ID));
        verify(terminalCommandService, never()).accept(any(), any());
    }

    @Test
    @DisplayName("在线车辆按 0x8201 受理并返回 commandId")
    void should_accept_position_query_when_online() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(vehicleService.listInScopeByIds(SCOPE, List.of(VEHICLE_ID))).thenReturn(List.of(vehicle()));
        when(positionStore.online(SIM_NO)).thenReturn(true);
        when(terminalCommandService.accept(any(), any())).thenReturn("cmd-1");

        String commandId = commandAppService.sendPositionQuery(USER_ID, VEHICLE_ID);

        assertEquals("cmd-1", commandId);
        verify(terminalCommandService).accept(any(), any());
    }

    @Test
    @DisplayName("车辆候选带回在线态，离线车辆仍可被搜索到")
    void should_mark_online_state_for_candidates() {
        VehicleRow online = vehicle();
        VehicleRow offline = vehicle();
        offline.setId(43L);
        offline.setPlateNo("辽B00002");
        offline.setSimNo("15880022240");
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(vehicleService.pageByScope(SCOPE, 1, 20, "辽B", null, null))
                .thenReturn(page(List.of(online, offline)));
        when(positionStore.onlineAll(anyCollection())).thenReturn(Map.of(SIM_NO, true));

        List<CommandVehicleVO> candidates = commandAppService.candidates(USER_ID, "辽B");

        assertEquals(2, candidates.size());
        assertTrue(candidates.get(0).isOnline());
        assertFalse(candidates.get(1).isOnline());
    }

    private IPage<VehicleRow> page(List<VehicleRow> rows) {
        Page<VehicleRow> page = new Page<>(1, 20, rows.size());
        page.setRecords(rows);
        return page;
    }

    private VehicleRow vehicle() {
        VehicleRow row = new VehicleRow();
        row.setId(VEHICLE_ID);
        row.setPlateNo("辽B00001");
        row.setSimNo(SIM_NO);
        row.setDepartmentId(99L);
        return row;
    }
} 
