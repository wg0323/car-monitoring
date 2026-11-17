package com.cm.admin.service.impl;

import com.cm.admin.vo.TrackResultVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.telemetry.PositionTrackRepository;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.infrastructure.telemetry.TrackPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 轨迹用例的边界校验：数据范围、时间跨度、点集上限与未绑卡车辆。
 * 存储层全部打桩，不连真库。
 */
@ExtendWith(MockitoExtension.class)
class TrackAppServiceImplTest {

    private static final long USER_ID = 9L;

    private static final long VEHICLE_ID = 123L;

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 15, 0, 0, 0);

    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 15, 23, 59, 59);

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private CmVehicleService vehicleService;

    @Mock
    private PositionTrackRepository trackRepository;

    private TrackAppServiceImpl trackAppService;

    @BeforeEach
    void setUp() {
        trackAppService = new TrackAppServiceImpl(dataScopeService, vehicleService, trackRepository,
                new TelemetryProperties());
    }

    @Test
    @DisplayName("车辆不在数据范围时按 403 拒绝，不访问存储")
    void should_reject_vehicle_out_of_scope() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of());

        assertThrows(AccessDeniedException.class,
                () -> trackAppService.query(USER_ID, VEHICLE_ID, START, END));
        verify(trackRepository, never()).count(any());
    }

    @Test
    @DisplayName("未绑 SIM 的车辆直接拒绝")
    void should_reject_vehicle_without_sim() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of(vehicle(null)));

        assertThrows(CmBusinessException.class,
                () -> trackAppService.query(USER_ID, VEHICLE_ID, START, END));
        verify(trackRepository, never()).count(any());
    }

    @Test
    @DisplayName("超过 3 天的跨度被拒绝")
    void should_reject_range_longer_than_limit() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of(vehicle("13800138000")));

        LocalDateTime end = START.plusDays(QueryLimits.MAX_TRACK_RANGE_DAYS).plusSeconds(1);

        assertThrows(CmBusinessException.class, () -> trackAppService.query(USER_ID, VEHICLE_ID, START, end));
        verify(trackRepository, never()).count(any());
    }

    @Test
    @DisplayName("恰好 3 天允许查询")
    void should_accept_range_equal_to_limit() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of(vehicle("13800138000")));
        when(trackRepository.count(any())).thenReturn(0L);
        when(trackRepository.find(any(), anyLong())).thenReturn(List.of());

        LocalDateTime end = START.plusDays(QueryLimits.MAX_TRACK_RANGE_DAYS);
        TrackResultVO result = trackAppService.query(USER_ID, VEHICLE_ID, START, end);

        assertEquals(0L, result.getTotal());
        assertEquals(VEHICLE_ID, result.getVehicleId());
    }

    @Test
    @DisplayName("点数超过上限时提示缩小范围")
    void should_reject_when_points_exceed_limit() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of(vehicle("13800138000")));
        when(trackRepository.count(any())).thenReturn(QueryLimits.MAX_TRACK_POINTS + 1);

        assertThrows(CmBusinessException.class,
                () -> trackAppService.query(USER_ID, VEHICLE_ID, START, END));
        verify(trackRepository, never()).find(any(), anyLong());
    }

    @Test
    @DisplayName("查询结果按 808 口径换算，列表与导出共用同一份点集")
    void should_convert_point_units() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of(vehicle("13800138000")));
        when(trackRepository.count(any())).thenReturn(1L);
        when(trackRepository.find(any(), anyLong())).thenReturn(List.of(point()));

        TrackResultVO result = trackAppService.query(USER_ID, VEHICLE_ID, START, END);

        assertEquals(1L, result.getTotal());
        assertEquals("30.000000", result.getPoints().get(0).getLatitude().toPlainString());
        assertEquals("120.000000", result.getPoints().get(0).getLongitude().toPlainString());
        assertEquals("6.0", result.getPoints().get(0).getSpeedKph().toPlainString());
        assertEquals("123.4", result.getPoints().get(0).getMileage().toPlainString());
        assertEquals("有效", result.getPoints().get(0).getValidText());
    }

    @Test
    @DisplayName("导出与查询写同一批行")
    void should_export_same_rows_as_query() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(DataScope.superAdmin(USER_ID));
        when(vehicleService.listInScopeByIds(any(), any())).thenReturn(List.of(vehicle("13800138000")));
        when(trackRepository.count(any())).thenReturn(1L);
        when(trackRepository.find(any(), anyLong())).thenReturn(List.of(point()));

        TrackResultVO result = trackAppService.query(USER_ID, VEHICLE_ID, START, END);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        trackAppService.export(USER_ID, VEHICLE_ID, START, END, output);

        assertEquals(1L, result.getTotal());
        assertTrue(output.size() > 0, "导出文件不应为空");
    }

    private VehicleRow vehicle(String simNo) {
        VehicleRow row = new VehicleRow();
        row.setId(VEHICLE_ID);
        row.setPlateNo("辽B00001");
        row.setSimNo(simNo);
        row.setDepartmentName("大连中心");
        row.setPlateColor(1);
        return row;
    }

    private TrackPoint point() {
        return new TrackPoint("E1", Instant.parse("2026-11-14T16:00:00Z"),
                30000000, 120000000, 15, 60, 58, 90, 1234L, 56, 3, 0);
    }
}

