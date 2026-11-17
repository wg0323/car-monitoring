package com.cm.infrastructure.service.impl;

import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.report.AlarmReportQuery;
import com.cm.domain.report.OnlineRecordQuery;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.mapper.CmAlarmRecordMapper;
import com.cm.infrastructure.persistence.mapper.ReportMapper;
import com.cm.infrastructure.persistence.mapper.VehicleOnlineReportMapper;
import com.cm.infrastructure.persistence.row.AlarmRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceImplTest {

    private static final DataScope SCOPE = DataScope.superAdmin(1L);

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static final LocalDateTime END = START.plusDays(1);

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private CmAlarmRecordMapper alarmRecordMapper;

    @Mock
    private VehicleOnlineReportMapper vehicleOnlineReportMapper;

    @InjectMocks
    private ReportQueryServiceImpl reportQueryService;

    @Test
    void should_reject_when_end_time_is_before_start_time() {
        assertThrows(CmBusinessException.class, () -> reportQueryService.operationLog(
                SCOPE, null, START, START.minusDays(1)));
        verifyNoInteractions(reportMapper);
    }

    @Test
    void should_reject_when_range_exceeds_limit() {
        LocalDateTime end = START.plusDays(QueryLimits.MAX_RANGE_DAYS + 1);

        assertThrows(CmBusinessException.class, () -> reportQueryService.alarmStatistic(SCOPE, START, end));
        verifyNoInteractions(reportMapper);
    }

    @Test
    void should_reject_unbounded_query_when_range_is_missing() {
        assertThrows(CmBusinessException.class, () -> reportQueryService.mileageStatistic(SCOPE, null, null));
        assertThrows(CmBusinessException.class, () -> reportQueryService.operationLog(SCOPE, null, START, null));
        assertThrows(CmBusinessException.class, () -> reportQueryService.alarmStatistic(SCOPE, null, END));
        verifyNoInteractions(reportMapper);
    }

    @Test
    void should_delegate_when_range_is_valid() {
        when(reportMapper.selectMileageStatistic(eq(SCOPE), eq(START), eq(END))).thenReturn(List.of());

        reportQueryService.mileageStatistic(SCOPE, START, END);

        verify(reportMapper).selectMileageStatistic(eq(SCOPE), eq(START), eq(END));
    }

    @Test
    void should_reject_when_scope_is_null() {
        assertThrows(CmBusinessException.class, () -> reportQueryService.departments(null));
        verifyNoInteractions(reportMapper);
    }

    @Test
    void should_validate_range_for_every_report_entry() {
        LocalDateTime end = START.plusDays(QueryLimits.MAX_RANGE_DAYS + 1);

        assertThrows(CmBusinessException.class, () -> reportQueryService.departmentOnlineRate(SCOPE, START, end));
        assertThrows(CmBusinessException.class, () -> reportQueryService.vehicleOnlineRate(SCOPE, START, end));
        assertThrows(CmBusinessException.class, () -> reportQueryService.onlineRecord(SCOPE, START, end, null));
        assertThrows(CmBusinessException.class, () -> reportQueryService.operationLog(SCOPE, null, START, end));
        verify(reportMapper, never()).selectDepartmentOnlineRate(any(), any(), any());
    }

    @Test
    void should_return_alarm_page_with_total() {
        AlarmReportQuery query = new AlarmReportQuery(null, "1", null, null, null, START, END);
        AlarmRow row = new AlarmRow();
        row.setPlateNo("辽B00001");
        when(alarmRecordMapper.countAlarmReport(SCOPE, null, "1", null, null, null, START, END)).thenReturn(1L);
        when(alarmRecordMapper.selectAlarmReportPage(SCOPE, null, "1", null, null, null, START, END, 0L, 20L))
                .thenReturn(List.of(row));

        var page = reportQueryService.alarmPage(SCOPE, query, 1, 20);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals("辽B00001", page.getRecords().get(0).getPlateNo());
    }

    @Test
    void should_reject_export_rows_when_max_rows_exceeds_limit() {
        AlarmReportQuery query = new AlarmReportQuery(null, null, null, null, null, START, END);

        assertThrows(CmBusinessException.class,
                () -> reportQueryService.alarmRows(SCOPE, query, QueryLimits.MAX_EXPORT_ROWS + 1));
        assertThrows(CmBusinessException.class, () -> reportQueryService.alarmRows(SCOPE, query, 0));
        verifyNoInteractions(alarmRecordMapper);
    }

    @Test
    void should_reject_invalid_page_for_report_queries() {
        AlarmReportQuery alarmQuery = new AlarmReportQuery(null, null, null, null, null, START, END);
        OnlineRecordQuery recordQuery = new OnlineRecordQuery(null, null, null, START, END);

        assertThrows(CmBusinessException.class, () -> reportQueryService.alarmPage(SCOPE, alarmQuery, 0, 20));
        assertThrows(CmBusinessException.class,
                () -> reportQueryService.onlineRecordPage(SCOPE, recordQuery, 1, QueryLimits.MAX_PAGE_SIZE + 1));
        verifyNoInteractions(alarmRecordMapper, reportMapper);
    }

    @Test
    void should_reject_online_record_when_range_is_missing() {
        OnlineRecordQuery query = new OnlineRecordQuery(null, null, null, null, null);

        assertThrows(CmBusinessException.class, () -> reportQueryService.onlineRecordPage(SCOPE, query, 1, 20));
        verifyNoInteractions(reportMapper);
    }

    @Test
    void should_reject_online_vehicle_candidates_when_scope_is_null() {
        assertThrows(CmBusinessException.class,
                () -> reportQueryService.onlineVehicleCandidates(null, null, null, null));
        verifyNoInteractions(vehicleOnlineReportMapper);
    }
}
