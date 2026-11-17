package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.domain.command.GovCommandQuery;
import com.cm.domain.command.GovLinkCommand;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.gov809.GovCommandStatus;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import com.cm.infrastructure.persistence.mapper.CmJt809CommandMapper;
import com.cm.infrastructure.service.CmJt809CommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 指令日志实现。链路命令不接受任意消息号，只有旧系统监管命令页实测的
 * 0x1001 / 0x1003 / 0x1007 / 0x1008 可入队，避免把日志表当通用下发通道。
 */
@Slf4j
@Service
public class CmJt809CommandServiceImpl extends ServiceImpl<CmJt809CommandMapper, CmJt809Command>
        implements CmJt809CommandService {

    private static final String SOURCE_SEND = "SEND";

    private static final int INITIAL_SN = 0;

    private static final long SYSTEM_COMPANY_ID = 0L;

    /** 与 cm-protocol 的 Jt809Constants.MSG_WARN_URGE_TODO / SUB_TYPE_URGE_TODO_ACK 对应。 */
    private static final int MSG_WARN_URGE_TODO = 0x1400;

    private static final int SUB_TYPE_URGE_TODO_ACK = 0x1401;

    /** 0x1401 的结果码：0 处理中、1 已处理完毕、2 不做处理、3 将来处理（旧系统下拉口径）。 */
    private static final int MIN_ACK_RESULT = 0;

    private static final int MAX_ACK_RESULT = 3;

    @Override
    public IPage<CmJt809Command> pageByQuery(GovCommandQuery query, long pageNo, long pageSize) {
        if (query == null) {
            throw new CmBusinessException("查询条件不能为空");
        }
        long offset = computeOffset(pageNo, pageSize);
        long total = baseMapper.countByQuery(query);
        List<CmJt809Command> rows = baseMapper.selectPageByQuery(query, offset, pageSize);
        Page<CmJt809Command> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public Long acceptLinkCommand(long platformId, int linkCmd, Long userId) {
        if (!GovLinkCommand.isSupported(linkCmd)) {
            throw new CmBusinessException("不支持的 809 链路命令");
        }
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(platformId);
        row.setSn(INITIAL_SN);
        row.setCmd(linkCmd);
        row.setSubCmd(0);
        row.setCmdData("");
        row.setDescr(GovLinkCommand.describe(linkCmd));
        row.setPlateNo("");
        row.setPlateColor(0);
        row.setSimNo("");
        row.setSource(SOURCE_SEND);
        row.setCommandStatus(GovCommandStatus.PENDING);
        row.setUserId(userId == null ? 0L : userId);
        row.setCompanyId(SYSTEM_COMPANY_ID);
        save(row);
        log.info("809 链路命令已受理, id={}, cmd=0x{}", row.getId(), Integer.toHexString(linkCmd));
        return row.getId();
    }

    @Override
    public Long acceptUrgeTodoAck(long platformId, long superviseId, String plateNo, int plateColor,
                                  int result, Long userId) {
        if (superviseId <= 0L) {
            throw new CmBusinessException("督办 ID 不能为空");
        }
        if (plateNo == null || plateNo.isBlank()) {
            throw new CmBusinessException("督办车牌不能为空");
        }
        if (result < MIN_ACK_RESULT || result > MAX_ACK_RESULT) {
            throw new CmBusinessException("督办应答结果码不在允许范围内");
        }
        CmJt809Command row = new CmJt809Command();
        row.setPlatformId(platformId);
        row.setSn(INITIAL_SN);
        row.setCmd(MSG_WARN_URGE_TODO);
        row.setSubCmd(SUB_TYPE_URGE_TODO_ACK);
        row.setCmdData(superviseId + ";" + result);
        row.setDescr("[0x1401]报警督办应答");
        row.setPlateNo(plateNo);
        row.setPlateColor(plateColor);
        row.setSimNo("");
        row.setSource(SOURCE_SEND);
        row.setCommandStatus(GovCommandStatus.PENDING);
        row.setUserId(userId == null ? 0L : userId);
        row.setCompanyId(SYSTEM_COMPANY_ID);
        save(row);
        log.info("809 督办应答已受理, id={}, superviseId={}, result={}", row.getId(), superviseId, result);
        return row.getId();
    }

    @Override
    public List<CmJt809Command> listPending(int limit) {
        return baseMapper.selectPending(limit);
    }

    @Override
    public boolean claim(long id) {
        return baseMapper.claimForExecution(id) == 1;
    }

    @Override
    public boolean markResult(long id, String status, String descr) {
        return baseMapper.markExecutionResult(id, status, descr) == 1;
    }

    @Override
    public int recoverStaleExecution(int staleMinutes) {
        if (staleMinutes <= 0) {
            throw new CmBusinessException("回收窗口必须大于 0 分钟");
        }
        int recovered = baseMapper.recoverStaleExecution(LocalDateTime.now().minusMinutes(staleMinutes));
        if (recovered > 0) {
            log.warn("809 链路命令执行超时已回收, count={}", recovered);
        }
        return recovered;
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
