package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.config.GovCommandProperties;
import com.cm.admin.converter.UrgeTodoViewConverter;
import com.cm.admin.service.UrgeTodoAppService;
import com.cm.admin.vo.UrgeTodoVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import com.cm.infrastructure.service.CmJt809CommandService;
import com.cm.infrastructure.service.UrgeTodoQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 督办实现。管理端不持有 809 连接：应答只写「待下发」指令，
 * 由企业侧链路进程抢占执行 0x1401，执行结果回写同一行指令记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgeTodoAppServiceImpl implements UrgeTodoAppService {

    /** 增量弹窗单次最多返回条数。 */
    private static final int NOTIFY_LIMIT = 20;

    /** 增量回看窗口上限：督办到达频率低，窗口放大到 1 天仍受条数限制。 */
    private static final int MAX_LOOKBACK_MINUTES = 1440;

    private final UrgeTodoQueryService urgeTodoQueryService;

    private final CmJt809CommandService commandService;

    private final GovCommandProperties properties;

    @Override
    public PageResult<UrgeTodoVO> page(long pageNo, long pageSize, String plateNo, Boolean acknowledged,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        IPage<CmWarnMsgUrgeTodoReq> page = urgeTodoQueryService.pageByQuery(plateNo, acknowledged,
                startTime, endTime, pageNo, pageSize);
        List<UrgeTodoVO> records = page.getRecords().stream()
                .map(UrgeTodoViewConverter::toVO)
                .toList();
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public List<UrgeTodoVO> notifications(long sinceId, int minutes) {
        int lookback = Math.min(Math.max(minutes, 1), MAX_LOOKBACK_MINUTES);
        List<CmWarnMsgUrgeTodoReq> rows = urgeTodoQueryService.listNewUrgeTodos(sinceId,
                LocalDateTime.now().minusMinutes(lookback), NOTIFY_LIMIT);
        return rows.stream().map(UrgeTodoViewConverter::toVO).toList();
    }

    @Override
    public Long acknowledge(Long userId, Long urgeTodoId, int result) {
        CmWarnMsgUrgeTodoReq row = urgeTodoQueryService.load(urgeTodoId == null ? 0L : urgeTodoId);
        if (row == null) {
            throw new CmBusinessException("督办不存在或已删除");
        }
        if (row.getAckTime() != null) {
            throw new CmBusinessException("该督办已应答，不能重复应答");
        }
        int plateColor = row.getPlateColor() == null ? 0 : row.getPlateColor();
        Long commandId = commandService.acceptUrgeTodoAck(properties.getPlatformId(),
                row.getSuperviseId(), row.getPlateNo(), plateColor, result, userId);
        log.info("809 督办应答已受理, urgeTodoId={}, superviseId={}, commandId={}, result={}",
                urgeTodoId, row.getSuperviseId(), commandId, result);
        return commandId;
    }
}
