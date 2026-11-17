package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import com.cm.infrastructure.persistence.mapper.CmWarnMsgUrgeTodoReqMapper;
import com.cm.infrastructure.service.UrgeTodoQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 督办读模型实现。督办属平台级审计数据，不按车辆部门过滤，
 * 与 809 报文查询同一口径（旧系统 paginate 同样未做部门隔离）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgeTodoQueryServiceImpl implements UrgeTodoQueryService {

    private static final long INVALID_ID = 0L;

    private final CmWarnMsgUrgeTodoReqMapper urgeTodoMapper;

    @Override
    public IPage<CmWarnMsgUrgeTodoReq> pageByQuery(String plateNo, Boolean acknowledged,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   long pageNo, long pageSize) {
        long offset = computeOffset(pageNo, pageSize);
        long total = urgeTodoMapper.countUrgeTodoPage(plateNo, acknowledged, startTime, endTime);
        List<CmWarnMsgUrgeTodoReq> rows = urgeTodoMapper.selectUrgeTodoPage(plateNo, acknowledged,
                startTime, endTime, offset, pageSize);
        Page<CmWarnMsgUrgeTodoReq> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public List<CmWarnMsgUrgeTodoReq> listNewUrgeTodos(long sinceId, LocalDateTime startTime, int limit) {
        if (limit < 1 || limit > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("增量查询条数超出允许范围");
        }
        return urgeTodoMapper.selectNewUrgeTodos(Math.max(sinceId, 0L), startTime, limit);
    }

    @Override
    public CmWarnMsgUrgeTodoReq load(long id) {
        if (id == INVALID_ID) {
            throw new CmBusinessException("督办 ID 不能为空");
        }
        return urgeTodoMapper.selectById(id);
    }

    private long computeOffset(long pageNo, long pageSize) {
        if (pageNo < 1 || pageNo > QueryLimits.MAX_PAGE_NO
                || pageSize < 1 || pageSize > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("分页参数超出允许范围");
        }
        try {
            return Math.multiplyExact(pageNo - 1, pageSize);
        } catch (ArithmeticException ex) {
            throw new CmBusinessException("分页偏移量溢出", ex);
        }
    }
}
