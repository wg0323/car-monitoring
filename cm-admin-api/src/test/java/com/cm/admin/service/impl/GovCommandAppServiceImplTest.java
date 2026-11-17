package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.admin.config.GovCommandProperties;
import com.cm.admin.vo.LinkStateVO;
import com.cm.domain.command.GovCommandQuery;
import com.cm.infrastructure.gov809.Gov809LinkService;
import com.cm.infrastructure.gov809.Gov809LinkSnapshot;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.service.CmJt809CommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GovCommandAppServiceImplTest {

    private static final long PLATFORM_ID = 8992L;

    @Mock
    private CmJt809CommandService commandService;

    @Mock
    private Gov809LinkService linkService;

    private GovCommandAppServiceImpl govCommandAppService;

    @BeforeEach
    void setUp() {
        GovCommandProperties properties = new GovCommandProperties();
        properties.setPlatformId(PLATFORM_ID);
        govCommandAppService = new GovCommandAppServiceImpl(commandService, linkService, properties);
    }

    @Test
    @DisplayName("链路命令落库时使用配置的接入码与当前操作人")
    void should_accept_link_command_with_platform_and_user() {
        when(commandService.acceptLinkCommand(PLATFORM_ID, 0x1001, 7L)).thenReturn(88L);

        Long id = govCommandAppService.sendLinkCommand(7L, 0x1001);

        assertEquals(88L, id);
        verify(commandService).acceptLinkCommand(PLATFORM_ID, 0x1001, 7L);
    }

    @Test
    @DisplayName("链路状态快照映射为主从状态与待补发条数")
    void should_map_link_state() {
        when(linkService.load(PLATFORM_ID, Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM))
                .thenReturn(new Gov809LinkSnapshot(PLATFORM_ID, Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM,
                        "LOGGED_IN", "DISCONNECTED", 10L, 9L, 3L, ""));

        LinkStateVO state = govCommandAppService.linkState();

        assertEquals("LOGGED_IN", state.getMainLinkState());
        assertEquals("DISCONNECTED", state.getSubLinkState());
        assertEquals(3L, state.getPendingCount());
    }

    @Test
    @DisplayName("分页查询透传条件并转换行")
    void should_page_with_query() {
        CmJt809Command row = new CmJt809Command();
        row.setId(1L);
        row.setCmd(0x1003);
        row.setSource("SEND");
        Page<CmJt809Command> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(row));
        GovCommandQuery query = new GovCommandQuery("SEND", null, null, null, null);
        when(commandService.pageByQuery(query, 1, 20)).thenReturn(page);

        var result = govCommandAppService.page(1, 20, query);

        assertEquals(1, result.getTotal());
        assertEquals("0x1003", result.getRecords().get(0).getCmdText());
        assertEquals("本端下发", result.getRecords().get(0).getSourceText());
    }
} 
