package com.cm.infrastructure.service.impl;

import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmAlarmProcess;
import com.cm.infrastructure.persistence.mapper.CmAlarmProcessMapper;
import com.cm.infrastructure.persistence.mapper.CmAlarmRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmAlarmRecordServiceImplTest {

    private static final Long ALARM_ID = 1001L;

    private static final DataScope SCOPE = DataScope.superAdmin(1L);

    @Mock
    private CmAlarmRecordMapper alarmRecordMapper;

    @Mock
    private CmAlarmProcessMapper alarmProcessMapper;

    private CmAlarmRecordServiceImpl alarmRecordService;

    @BeforeEach
    void setUp() {
        alarmRecordService = new CmAlarmRecordServiceImpl(alarmProcessMapper);
        ReflectionTestUtils.setField(alarmRecordService, "baseMapper", alarmRecordMapper);
    }

    @Test
    void should_fail_and_skip_audit_when_no_row_was_updated() {
        when(alarmRecordMapper.markProcessed(any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(SCOPE, ALARM_ID, 2L, "处理人", "PROCESSED", ""));

        verify(alarmProcessMapper, never()).insert(any(CmAlarmProcess.class));
    }

    @Test
    void should_write_audit_with_expected_fields_when_state_update_succeeds() {
        when(alarmRecordMapper.markProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1);
        ArgumentCaptor<CmAlarmProcess> captor = ArgumentCaptor.forClass(CmAlarmProcess.class);

        alarmRecordService.process(SCOPE, ALARM_ID, 2L, "处理人", "PROCESSED", "已核实");

        verify(alarmProcessMapper).insert(captor.capture());
        CmAlarmProcess audit = captor.getValue();
        assertEquals(Long.valueOf(ALARM_ID), audit.getAlarmId());
        assertEquals(Long.valueOf(2L), audit.getProcessorId());
        assertEquals("处理人", audit.getProcessorName());
        assertEquals("PROCESSED", audit.getProcessResult());
        assertEquals("已核实", audit.getProcessRemark());
    }

    /**
     * 旧系统把 remark 只写审计表，报警行本身留空；TC-DAT-03 的判据是三个字段都在报警行上，
     * 所以这里锁住「备注随条件更新一起写进 cm_alarm_record」。
     */
    @Test
    void should_pass_remark_into_alarm_row_update() {
        when(alarmRecordMapper.markProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1);

        alarmRecordService.process(SCOPE, ALARM_ID, 2L, "处理人", "PROCESSED", "已核实");

        verify(alarmRecordMapper).markProcessed(eq(SCOPE), eq(ALARM_ID), eq(2L), eq("处理人"),
                eq("已核实"), any());
    }

    @Test
    void should_pass_scope_into_conditional_update() {
        when(alarmRecordMapper.markProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1);

        alarmRecordService.process(SCOPE, ALARM_ID, 2L, "处理人", "PROCESSED", "");

        verify(alarmRecordMapper).markProcessed(eq(SCOPE), eq(ALARM_ID), any(), anyString(), any(), any());
    }

    @Test
    void should_reject_when_scope_is_missing() {
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(null, ALARM_ID, 2L, "处理人", "PROCESSED", ""));

        verify(alarmRecordMapper, never()).markProcessed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_reject_when_alarm_id_is_missing() {
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(SCOPE, null, 2L, "处理人", "PROCESSED", ""));

        verify(alarmRecordMapper, never()).markProcessed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_reject_when_required_parameters_are_invalid() {
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(SCOPE, ALARM_ID, null, "处理人", "PROCESSED", ""));
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(SCOPE, ALARM_ID, 2L, null, "PROCESSED", ""));
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(SCOPE, ALARM_ID, 2L, "处理人", null, ""));
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.process(SCOPE, ALARM_ID, 2L, "处理人", "X".repeat(33), ""));

        verify(alarmRecordMapper, never()).markProcessed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_reject_when_page_number_would_overflow_offset() {
        assertThrows(CmBusinessException.class,
                () -> alarmRecordService.pageByScope(SCOPE, null, null, null, null, null,
                        Long.MAX_VALUE, 1000L));
    }
}
