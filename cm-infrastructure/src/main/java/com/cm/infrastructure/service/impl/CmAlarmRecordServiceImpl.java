package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.entity.CmAlarmProcess;
import com.cm.infrastructure.persistence.entity.CmAlarmRecord;
import com.cm.infrastructure.persistence.mapper.CmAlarmProcessMapper;
import com.cm.infrastructure.persistence.mapper.CmAlarmRecordMapper;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.service.CmAlarmRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警处理实现。旧系统查不到记录时返回空实体并 success:true 但更新 0 行（DEF-043），
 * 这里改为条件更新 + 影响行数断言，并把数据范围下推到 UPDATE 的 WHERE 里，堵住写侧越权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmAlarmRecordServiceImpl extends ServiceImpl<CmAlarmRecordMapper, CmAlarmRecord>
        implements CmAlarmRecordService {

    private static final long INVALID_ID = 0L;

    private static final int MAX_RESULT_LENGTH = 32;

    private static final int MAX_NAME_LENGTH = 64;

    private static final int MAX_REMARK_LENGTH = 255;

    private final CmAlarmProcessMapper alarmProcessMapper;

    @Override
    public IPage<AlarmRow> pageByScope(DataScope scope, String alarmType, Boolean processed, String plateNo,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       long pageNo, long pageSize) {
        requireScope(scope);
        long offset = computeOffset(pageNo, pageSize);
        long total = baseMapper.countAlarmPage(scope, alarmType, processed, plateNo, startTime, endTime);
        List<AlarmRow> rows = baseMapper.selectAlarmPage(scope, alarmType, processed, plateNo,
                startTime, endTime, offset, pageSize);
        Page<AlarmRow> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public List<AlarmRow> listNewAlarms(DataScope scope, long sinceId, LocalDateTime startTime, int limit) {
        requireScope(scope);
        if (limit < 1 || limit > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("增量查询条数超出允许范围");
        }
        return baseMapper.selectNewAlarms(scope, Math.max(sinceId, 0L), startTime, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(DataScope scope, Long alarmId, Long processorId, String processorName,
                        String result, String remark) {
        requireScope(scope);
        validateInput(alarmId, processorId, processorName, result, remark);
        LocalDateTime now = LocalDateTime.now();
        int affected = baseMapper.markProcessed(scope, alarmId, processorId, processorName, remark, now);
        if (affected == 0) {
            throw new CmBusinessException("报警不存在、已处理或不在可访问范围内");
        }
        CmAlarmProcess audit = new CmAlarmProcess();
        audit.setAlarmId(alarmId);
        audit.setProcessorId(processorId);
        audit.setProcessorName(processorName);
        audit.setProcessResult(result);
        audit.setProcessRemark(remark == null ? "" : remark);
        audit.setProcessTime(now);
        alarmProcessMapper.insert(audit);
        log.info("报警处理完成, alarmId={}, processorId={}", alarmId, processorId);
    }

    @Override
    public boolean tryOpenAlarm(CmAlarmRecord record) {
        try {
            baseMapper.insert(record);
            return true;
        } catch (DuplicateKeyException ex) {
            // 同一 event_key 已存在：终端重报、MQ 重投或死信重放都走这里，不影响已落库的记录。
            log.debug("报警已存在，跳过开户, eventKey={}", record.getEventKey());
            return false;
        }
    }

    @Override
    public int closeOpenAlarm(String simNo, String alarmType, String alarmChannel, LocalDateTime endTime,
                              BigDecimal latitude, BigDecimal longitude, BigDecimal mileage, BigDecimal gas) {
        return baseMapper.closeOpenAlarm(simNo, alarmType, alarmChannel, endTime, latitude, longitude, mileage, gas);
    }

    @Override
    public List<String> openAlarmTypes(String simNo, String alarmChannel) {
        return baseMapper.selectOpenAlarmTypes(simNo, alarmChannel);
    }

    private void requireScope(DataScope scope) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
    }

    private void validateInput(Long alarmId, Long processorId, String processorName,
                               String result, String remark) {
        if (alarmId == null || alarmId.longValue() == INVALID_ID
                || processorId == null || processorId.longValue() == INVALID_ID) {
            throw new CmBusinessException("报警 ID 与处理人 ID 不能为空");
        }
        if (processorName == null || processorName.isBlank() || processorName.length() > MAX_NAME_LENGTH) {
            throw new CmBusinessException("处理人名称不合法");
        }
        if (result == null || result.isBlank() || result.length() > MAX_RESULT_LENGTH) {
            throw new CmBusinessException("处理结果不能为空且不能超过 " + MAX_RESULT_LENGTH + " 字符");
        }
        if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
            throw new CmBusinessException("处理说明不能超过 " + MAX_REMARK_LENGTH + " 字符");
        }
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
