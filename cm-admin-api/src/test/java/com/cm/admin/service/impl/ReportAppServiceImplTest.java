package com.cm.admin.service.impl;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.ReportOnlineVehicleVO;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.monitor.PositionSnapshot;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.service.ReportQueryService;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 当前在线报表：MySQL 候选与 Redis 在线态、位置合并后的过滤、离线天数与分页。
 */
@ExtendWith(MockitoExtension.class)
class ReportAppServiceImplTest {

    private static final DataScope SCOPE = DataScope.superAdmin(1L);

    private static final String SIM_A = "SIM-A";

    private static final String SIM_B = "SIM-B";

    private static final String SIM_C = "SIM-C";

    @Mock
    private ReportQueryService reportQueryService;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private MonitorPositionStore monitorPositionStore;

    private ReportAppServiceImpl reportAppService;

    @BeforeEach
    void setUp() {
        reportAppService = new ReportAppServiceImpl(reportQueryService, dataScopeService,
                monitorPositionStore, new TelemetryProperties());
    }

    @Test
    void should_filter_offline_vehicles_by_offline_days() {
        stubCandidates();
        when(dataScopeService.resolve(1L)).thenReturn(SCOPE);
        when(monitorPositionStore.onlineAll(List.of(SIM_A, SIM_B, SIM_C)))
                .thenReturn(Map.of(SIM_A, true, SIM_B, false, SIM_C, false));
        when(monitorPositionStore.latestAll(List.of(SIM_A, SIM_B, SIM_C)))
                .thenReturn(Map.of(SIM_A, snapshot(Duration.ofMinutes(5)),
                        SIM_B, snapshot(Duration.ofDays(2))));

        PageResult<ReportOnlineVehicleVO> result = reportAppService.onlineVehiclePage(
                1L, 1, 20, null, null, null, "offline", 1);

        assertEquals(1L, result.getTotal());
        ReportOnlineVehicleVO vo = result.getRecords().get(0);
        assertEquals("辽B00002", vo.getPlateNo());
        assertEquals("离线", vo.getOnlineStatusName());
        assertTrue(vo.getOfflineMinutes() >= 2878 && vo.getOfflineMinutes() <= 2882,
                "离线时长应约等于 2 天，实际 " + vo.getOfflineMinutes());
    }

    @Test
    void should_return_online_vehicles_with_zero_offline_minutes() {
        stubCandidates();
        when(dataScopeService.resolve(1L)).thenReturn(SCOPE);
        when(monitorPositionStore.onlineAll(List.of(SIM_A, SIM_B, SIM_C)))
                .thenReturn(Map.of(SIM_A, true, SIM_B, false, SIM_C, false));
        when(monitorPositionStore.latestAll(List.of(SIM_A, SIM_B, SIM_C)))
                .thenReturn(Map.of(SIM_A, snapshot(Duration.ofMinutes(5))));

        PageResult<ReportOnlineVehicleVO> result = reportAppService.onlineVehiclePage(
                1L, 1, 20, null, null, null, "online", null);

        assertEquals(1L, result.getTotal());
        assertEquals("辽B00001", result.getRecords().get(0).getPlateNo());
        assertEquals(0L, result.getRecords().get(0).getOfflineMinutes());
    }

    @Test
    void should_page_all_candidates_in_plate_order() {
        stubCandidates();
        when(dataScopeService.resolve(1L)).thenReturn(SCOPE);
        when(monitorPositionStore.onlineAll(List.of(SIM_A, SIM_B, SIM_C)))
                .thenReturn(Map.of(SIM_A, true, SIM_B, false, SIM_C, false));
        when(monitorPositionStore.latestAll(List.of(SIM_A, SIM_B, SIM_C)))
                .thenReturn(Map.of(SIM_A, snapshot(Duration.ofMinutes(5)),
                        SIM_B, snapshot(Duration.ofDays(2))));

        PageResult<ReportOnlineVehicleVO> result = reportAppService.onlineVehiclePage(
                1L, 2, 2, null, null, null, null, null);

        assertEquals(3L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("辽B00003", result.getRecords().get(0).getPlateNo());
    }

    private void stubCandidates() {
        when(reportQueryService.onlineVehicleCandidates(SCOPE, null, null, null))
                .thenReturn(List.of(candidate(1L, "辽B00001", SIM_A),
                        candidate(2L, "辽B00002", SIM_B),
                        candidate(3L, "辽B00003", SIM_C)));
    }

    private VehicleOnlineReportRow candidate(Long id, String plateNo, String simNo) {
        VehicleOnlineReportRow row = new VehicleOnlineReportRow();
        row.setId(id);
        row.setPlateNo(plateNo);
        row.setPlateColor(1);
        row.setSimNo(simNo);
        row.setDepartmentName("测试车组");
        row.setRunStatus("运营");
        row.setVehicleType("货车");
        return row;
    }

    private PositionSnapshot snapshot(Duration age) {
        return new PositionSnapshot(30200000, 120000000, 0, 90, 0, 1,
                Instant.now().minus(age).toString());
    }
}
 
