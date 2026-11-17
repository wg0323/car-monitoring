package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.admin.common.PageResult;
import com.cm.admin.config.GovCommandProperties;
import com.cm.admin.vo.UrgeTodoVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import com.cm.infrastructure.service.CmJt809CommandService;
import com.cm.infrastructure.service.UrgeTodoQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrgeTodoAppServiceImplTest {

    private static final long USER_ID = 7L;

    private static final long URGE_TODO_ID = 501L;

    private static final long SUPERVISE_ID = 1L;

    private static final long PLATFORM_ID = 8992L;

    @Mock
    private UrgeTodoQueryService urgeTodoQueryService;

    @Mock
    private CmJt809CommandService commandService;

    private UrgeTodoAppServiceImpl urgeTodoAppService;

    @BeforeEach
    void setUp() {
        GovCommandProperties properties = new GovCommandProperties();
        properties.setPlatformId(PLATFORM_ID);
        urgeTodoAppService = new UrgeTodoAppServiceImpl(urgeTodoQueryService, commandService, properties);
    }

    @Test
    @DisplayName("应答写入 0x1401 待下发指令，不停留在管理端直发")
    void should_accept_ack_as_pending_command() {
        when(urgeTodoQueryService.load(URGE_TODO_ID)).thenReturn(urgeTodo(false));
        when(commandService.acceptUrgeTodoAck(PLATFORM_ID, SUPERVISE_ID, "甘A43060", 1, 1, USER_ID))
                .thenReturn(9001L);

        Long commandId = urgeTodoAppService.acknowledge(USER_ID, URGE_TODO_ID, 1);

        assertEquals(9001L, commandId);
        verify(commandService).acceptUrgeTodoAck(PLATFORM_ID, SUPERVISE_ID, "甘A43060", 1, 1, USER_ID);
    }

    @Test
    @DisplayName("已应答的督办不能重复应答")
    void should_reject_repeated_ack() {
        CmWarnMsgUrgeTodoReq row = urgeTodo(true);
        row.setAckTime(LocalDateTime.now());
        when(urgeTodoQueryService.load(URGE_TODO_ID)).thenReturn(row);

        assertThrows(CmBusinessException.class, () -> urgeTodoAppService.acknowledge(USER_ID, URGE_TODO_ID, 1));
        verify(commandService, never()).acceptUrgeTodoAck(anyLong(), anyLong(), any(), anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("督办不存在时拒绝应答")
    void should_reject_missing_urge_todo() {
        when(urgeTodoQueryService.load(URGE_TODO_ID)).thenReturn(null);

        assertThrows(CmBusinessException.class, () -> urgeTodoAppService.acknowledge(USER_ID, URGE_TODO_ID, 1));
        verify(commandService, never()).acceptUrgeTodoAck(anyLong(), anyLong(), any(), anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("分页把码值翻译成中文并标记应答状态")
    void should_page_with_translated_text() {
        when(urgeTodoQueryService.pageByQuery(eq("甘A"), eq(false), any(), any(), eq(1L), eq(10L)))
                .thenReturn(page(List.of(urgeTodo(false))));

        PageResult<UrgeTodoVO> result = urgeTodoAppService.page(1, 10, "甘A", false, null, null);

        assertEquals(1, result.getTotal());
        UrgeTodoVO vo = result.getRecords().get(0);
        assertEquals("车载终端", vo.getWarnSourceText());
        assertEquals("超速", vo.getWarnTypeText());
        assertEquals("紧急", vo.getSupervisionLevelText());
        assertFalse(vo.getAcknowledged());
        assertEquals("未应答", vo.getAckResultText());
        assertNull(vo.getAckResult());
    }

    @Test
    @DisplayName("增量回看窗口上限 1 天")
    void should_clamp_notification_lookback() {
        when(urgeTodoQueryService.listNewUrgeTodos(eq(3L), any(), eq(20))).thenReturn(List.of(urgeTodo(false)));

        List<UrgeTodoVO> rows = urgeTodoAppService.notifications(3L, 99999);

        assertEquals(1, rows.size());
        verify(urgeTodoQueryService).listNewUrgeTodos(eq(3L), any(), eq(20));
    }

    private IPage<CmWarnMsgUrgeTodoReq> page(List<CmWarnMsgUrgeTodoReq> rows) {
        Page<CmWarnMsgUrgeTodoReq> page = new Page<>(1, 10, rows.size());
        page.setRecords(rows);
        return page;
    }

    private CmWarnMsgUrgeTodoReq urgeTodo(boolean acknowledged) {
        CmWarnMsgUrgeTodoReq row = new CmWarnMsgUrgeTodoReq();
        row.setId(URGE_TODO_ID);
        row.setPlatformId(PLATFORM_ID);
        row.setSuperviseId(SUPERVISE_ID);
        row.setPlateNo("甘A43060");
        row.setPlateColor(1);
        row.setWarnSrc(1);
        row.setWarnType(1);
        row.setWarnTime(LocalDateTime.of(2026, 11, 17, 13, 20, 43));
        row.setSupervisionEndTime(LocalDateTime.of(2026, 11, 17, 18, 0));
        row.setSupervisionLevel(0);
        row.setSupervisor("张三");
        row.setSupervisorTel("13800000000");
        row.setSupervisorEmail("zhangsan@example.com");
        row.setAckResult(acknowledged ? 1 : 0);
        row.setCreateTime(LocalDateTime.of(2026, 11, 17, 13, 21));
        return row;
    }
}
