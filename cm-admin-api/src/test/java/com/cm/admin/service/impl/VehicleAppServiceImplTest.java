package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.admin.common.PageResult;
import com.cm.admin.monitor.OnlineVehicleResolver;
import com.cm.admin.vo.VehicleDetailVO;
import com.cm.admin.vo.VehicleVO;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.VehicleDetailRow;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleAppServiceImplTest {

    private static final long USER_ID = 100000000000120201L;

    private static final long DEPARTMENT_ID = 100000000117440544L;

    private static final long VEHICLE_ID = 100000000000000901L;

    @Mock
    private CmVehicleService vehicleService;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private OnlineVehicleResolver onlineVehicleResolver;

    private VehicleAppServiceImpl service;

    private DataScope scope;

    @BeforeEach
    void setUp() {
        service = new VehicleAppServiceImpl(vehicleService, dataScopeService, onlineVehicleResolver);
        scope = new DataScope(USER_ID, false, List.of(1L), List.of(DEPARTMENT_ID));
    }

    @Test
    @DisplayName("详情越界与不存在都按 403 拒绝，不区分原因")
    void should_reject_vehicle_detail_outside_scope() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(vehicleService.detailInScope(scope, VEHICLE_ID)).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> service.detail(USER_ID, VEHICLE_ID));
    }

    @Test
    @DisplayName("详情把车牌颜色编码翻成中文名")
    void should_translate_plate_color_name() {
        VehicleDetailRow row = new VehicleDetailRow();
        row.setId(VEHICLE_ID);
        row.setPlateNo("辽B00001");
        row.setPlateColor(3);
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(vehicleService.detailInScope(scope, VEHICLE_ID)).thenReturn(row);

        VehicleDetailVO detail = service.detail(USER_ID, VEHICLE_ID);

        assertEquals("辽B00001", detail.getPlateNo());
        assertEquals("白色", detail.getPlateColorName());
    }

    @Test
    @DisplayName("在线筛选把解析后的 SIM 清单原样下推给持久层")
    void should_pass_online_filter_to_repository() {
        List<String> onlineSims = List.of("13800138000");
        VehicleRow row = new VehicleRow();
        row.setId(VEHICLE_ID);
        row.setPlateNo("辽B00001");
        IPage<VehicleRow> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(row));
        when(dataScopeService.resolve(USER_ID)).thenReturn(scope);
        when(onlineVehicleResolver.resolve(scope, true)).thenReturn(onlineSims);
        when(vehicleService.pageByScope(eq(scope), anyLong(), anyLong(), any(), any(), any(), eq(true),
                eq(onlineSims))).thenReturn(page);

        PageResult<VehicleVO> result = service.page(USER_ID, 1, 20, null, null, null, true);

        assertEquals(1, result.getTotal());
        assertEquals("辽B00001", result.getRecords().get(0).getPlateNo());
    }
}

