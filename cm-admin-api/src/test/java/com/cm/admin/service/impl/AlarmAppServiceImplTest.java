package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.admin.common.PageResult;
import com.cm.admin.vo.AlarmProcessResultVO;
import com.cm.admin.vo.AlarmRecordVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.service.CmAlarmRecordService;
import com.cm.infrastructure.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmAppServiceImplTest {

    private static final long USER_ID = 1L;

    private static final String USER_NAME = "系统管理员";

    private static final DataScope SCOPE = new DataScope(USER_ID, false, List.of(9L), List.of(99L));

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 17, 0, 0);

    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 17, 23, 59, 59);

    @Mock
    private CmAlarmRecordService alarmRecordService;

    @Mock
    private DataScopeService dataScopeService;

    private AlarmAppServiceImpl alarmAppService;

    @BeforeEach
    void setUp() {
        alarmAppService = new AlarmAppServiceImpl(alarmRecordService, dataScopeService);
    }

    @Test
    @DisplayName("分页按数据范围查询并带出定位坐标")
    void should_page_with_scope_and_coordinates() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(alarmRecordService.pageByScope(eq(SCOPE), eq(null), eq(null), eq(null), eq(START), eq(END),
                eq(1L), eq(10L))).thenReturn(page(List.of(alarmRow())));

        PageResult<AlarmRecordVO> result = alarmAppService.page(USER_ID, 1, 10, null, null, START, END);

        assertEquals(1, result.getTotal());
        AlarmRecordVO vo = result.getRecords().get(0);
        assertEquals("辽B00001", vo.getPlateNo());
        assertEquals("超速报警", vo.getAlarmTypeName());
        assertEquals("终端报警", vo.getAlarmSourceName());
        assertEquals(new BigDecimal("38.914000"), vo.getLatitude());
        assertEquals(new BigDecimal("121.614000"), vo.getLongitude());
    }

    @Test
    @DisplayName("处理状态与车牌前缀原样下推给查询")
    void should_pass_processed_and_plate_prefix_to_query() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(alarmRecordService.pageByScope(eq(SCOPE), eq(null), eq(false), eq("辽B"), eq(START), eq(END),
                eq(1L), eq(10L))).thenReturn(page(List.of()));

        alarmAppService.page(USER_ID, 1, 10, false, "辽B", START, END);

        verify(alarmRecordService).pageByScope(SCOPE, null, false, "辽B", START, END, 1L, 10L);
    }

    @Test
    @DisplayName("时间窗缺失或倒置时拒绝查询")
    void should_reject_invalid_time_range() {
        assertThrows(CmBusinessException.class,
                () -> alarmAppService.page(USER_ID, 1, 10, null, null, null, END));
        assertThrows(CmBusinessException.class,
                () -> alarmAppService.page(USER_ID, 1, 10, null, null, END, START));
        verify(alarmRecordService, never()).pageByScope(any(), any(), any(), any(), any(), any(), anyLong(),
                anyLong());
    }

    @Test
    @DisplayName("增量轮询把回看分钟数限幅后下推给查询")
    void should_clamp_lookback_minutes() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(alarmRecordService.listNewAlarms(eq(SCOPE), eq(7L), any(), eq(50)))
                .thenReturn(List.of(alarmRow()));

        assertEquals(1, alarmAppService.notifications(USER_ID, 7L, 9999).size());
        verify(alarmRecordService).listNewAlarms(eq(SCOPE), eq(7L), any(), eq(50));
    }

    @Test
    @DisplayName("回看分钟数小于 1 时按 1 分钟取数")
    void should_use_minimum_lookback() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        when(alarmRecordService.listNewAlarms(eq(SCOPE), eq(0L), any(), anyInt())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        alarmAppService.notifications(USER_ID, 0L, 0);
        LocalDateTime after = LocalDateTime.now().minusMinutes(1);

        // 时间窗在调用瞬间生成，只断言它落在「1 分钟前」的合理区间内。
        verify(alarmRecordService).listNewAlarms(eq(SCOPE), eq(0L),
                org.mockito.ArgumentMatchers.argThat(time -> time != null
                        && !time.isBefore(before.minusSeconds(5))
                        && !time.isAfter(after.plusSeconds(5))), anyInt());
    }

    @Test
    @DisplayName("单条处理把处理结果固定为已处理并带上登录人")
    void should_process_alarm_with_login_principal() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);

        alarmAppService.process(USER_ID, USER_NAME, 1001L, "已核实");

        verify(alarmRecordService).process(SCOPE, 1001L, USER_ID, USER_NAME, "PROCESSED", "已核实");
    }

    @Test
    @DisplayName("报警 ID 为空或非正数时拒绝处理")
    void should_reject_invalid_alarm_id() {
        assertThrows(CmBusinessException.class,
                () -> alarmAppService.process(USER_ID, USER_NAME, null, ""));
        assertThrows(CmBusinessException.class,
                () -> alarmAppService.process(USER_ID, USER_NAME, 0L, ""));

        verify(dataScopeService, never()).resolve(anyLong());
    }

    @Test
    @DisplayName("备注超过 255 字符时拒绝处理")
    void should_reject_overlong_remark() {
        assertThrows(CmBusinessException.class,
                () -> alarmAppService.process(USER_ID, USER_NAME, 1001L, "X".repeat(256)));

        verify(dataScopeService, never()).resolve(anyLong());
    }

    @Test
    @DisplayName("批量处理按条独立提交，失败项带原因跳过")
    void should_batch_process_and_report_skipped() {
        when(dataScopeService.resolve(USER_ID)).thenReturn(SCOPE);
        // 用参数匹配器整体接管：只有 id=2 抛业务异常，其余照常成功，避免苛刻桩判定误伤。
        doAnswer(invocation -> {
            if (Long.valueOf(2L).equals(invocation.getArgument(1))) {
                throw new CmBusinessException("报警不存在、已处理或不在可访问范围内");
            }
            return null;
        }).when(alarmRecordService).process(eq(SCOPE), anyLong(), eq(USER_ID), eq(USER_NAME),
                eq("PROCESSED"), eq("批量"));

        AlarmProcessResultVO result = alarmAppService.processBatch(USER_ID, USER_NAME,
                List.of(1L, 2L, 2L, 3L), "批量");

        assertEquals(2, result.getProcessed(), "重复 ID 只处理一次，成功 1 与 3 共两条");
        assertEquals(1, result.getSkipped().size());
        assertEquals("2", result.getSkipped().get(0).getId());
        assertEquals("报警不存在、已处理或不在可访问范围内", result.getSkipped().get(0).getReason());
        verify(alarmRecordService).process(SCOPE, 1L, USER_ID, USER_NAME, "PROCESSED", "批量");
        verify(alarmRecordService).process(SCOPE, 3L, USER_ID, USER_NAME, "PROCESSED", "批量");
    }

    @Test
    @DisplayName("批量处理超过 100 条直接拒绝，不进入逐条事务")
    void should_reject_batch_over_limit() {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThrows(CmBusinessException.class,
                () -> alarmAppService.processBatch(USER_ID, USER_NAME, ids, ""));

        verify(alarmRecordService, never()).process(any(), anyLong(), anyLong(), anyString(), anyString(),
                anyString());
    }

    @Test
    @DisplayName("空选择不发起批量处理")
    void should_reject_empty_batch() {
        assertThrows(CmBusinessException.class,
                () -> alarmAppService.processBatch(USER_ID, USER_NAME, List.of(), ""));

        verify(alarmRecordService, never()).process(any(), anyLong(), anyLong(), anyString(), anyString(),
                anyString());
    }

    private IPage<AlarmRow> page(List<AlarmRow> rows) {
        Page<AlarmRow> page = new Page<>(1, 10, rows.size());
        page.setRecords(rows);
        return page;
    }

    private AlarmRow alarmRow() {
        AlarmRow row = new AlarmRow();
        row.setId(1001L);
        row.setPlateNo("辽B00001");
        row.setPlateColor(1);
        row.setSimNo("15880022239");
        row.setVehicleId(42L);
        row.setAlarmType("1");
        row.setAlarmSource("terminal_alarm");
        row.setStartTime(START.plusHours(8));
        row.setLocation("");
        row.setLatitude(new BigDecimal("38.914000"));
        row.setLongitude(new BigDecimal("121.614000"));
        row.setProcessed(false);
        return row;
    }
}
