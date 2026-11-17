package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.config.GovCommandProperties;
import com.cm.admin.converter.CommandViewConverter;
import com.cm.admin.service.GovCommandAppService;
import com.cm.admin.vo.GovCommandVO;
import com.cm.admin.vo.LinkStateVO;
import com.cm.domain.command.GovCommandQuery;
import com.cm.infrastructure.gov809.Gov809LinkService;
import com.cm.infrastructure.gov809.Gov809LinkSnapshot;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.service.CmJt809CommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 809 命令实现。链路命令只入队不直发：企业侧链路进程是唯一持有 809 连接的地方，
 * 管理端写库后由它抢占执行并回写结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovCommandAppServiceImpl implements GovCommandAppService {

    private final CmJt809CommandService commandService;

    private final Gov809LinkService linkService;

    private final GovCommandProperties properties;

    @Override
    public PageResult<GovCommandVO> page(long pageNo, long pageSize, GovCommandQuery query) {
        IPage<CmJt809Command> page = commandService.pageByQuery(query, pageNo, pageSize);
        List<GovCommandVO> records = page.getRecords().stream()
                .map(CommandViewConverter::toGovCommandVO)
                .toList();
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public Long sendLinkCommand(Long userId, int linkCmd) {
        return commandService.acceptLinkCommand(properties.getPlatformId(), linkCmd, userId);
    }

    @Override
    public LinkStateVO linkState() {
        Gov809LinkSnapshot snapshot = linkService.load(properties.getPlatformId(),
                Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM);
        LinkStateVO vo = new LinkStateVO();
        vo.setPlatformId(properties.getPlatformId());
        vo.setMainLinkState(snapshot.mainLinkState());
        vo.setSubLinkState(snapshot.subLinkState());
        vo.setPendingCount(snapshot.pendingCount());
        vo.setLastError(snapshot.lastError() == null ? "" : snapshot.lastError());
        return vo;
    }
}
