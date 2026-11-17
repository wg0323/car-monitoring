package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.AlarmViewConverter;
import com.cm.admin.service.AlarmAppService;
import com.cm.admin.vo.AlarmNotificationVO;
import com.cm.admin.vo.AlarmProcessResultVO;
import com.cm.admin.vo.AlarmRecordVO;
import com.cm.admin.vo.AlarmSkipVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.AlarmRow;
import com.cm.infrastructure.service.CmAlarmRecordService;
import com.cm.infrastructure.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 报警处理实现。数据范围与报表、实时监控同口径：范围只由登录主体生成，
 * 查询不接受客户端自带的部门参数，处理动作把范围下推到 UPDATE 的 WHERE 里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmAppServiceImpl implements AlarmAppService {

    /** 单次轮询最多返回条数：超出部分下一轮按 sinceId 继续取，不做分页。 */
    private static final int NOTIFY_LIMIT = 50;

    /** 轮询回看窗口上限：页面 5 秒一轮，窗口只用于挡住异常大的回看请求。 */
    private static final int MAX_LOOKBACK_MINUTES = 60;

    /** 本轮只做「已处理」一种结果；解除报警要下发 0x8203 与 809 0x1403，不在本切片范围。 */
    private static final String PROCESS_RESULT = "PROCESSED";

    /** 批量处理单次上限：与旧系统一次提交多行的用法一致，但不允许整页一次提交。 */
    private static final int MAX_BATCH = 100;

    private static final int MAX_REMARK_LENGTH = 255;

    private final CmAlarmRecordService alarmRecordService;

    private final DataScopeService dataScopeService;

    @Override
    public PageResult<AlarmRecordVO> page(Long userId, long pageNo, long pageSize, Boolean processed,
                                          String plateNo, LocalDateTime startTime, LocalDateTime endTime) {
        requireTimeRange(startTime, endTime);
        DataScope scope = dataScopeService.resolve(userId);
        IPage<AlarmRow> page = alarmRecordService.pageByScope(scope, null, processed, plateNo,
                startTime, endTime, pageNo, pageSize);
        List<AlarmRecordVO> records = page.getRecords().stream()
                .map(AlarmViewConverter::toRecordVO)
                .toList();
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public List<AlarmNotificationVO> notifications(Long userId, long sinceId, int minutes) {
        int lookback = Math.min(Math.max(minutes, 1), MAX_LOOKBACK_MINUTES);
        DataScope scope = dataScopeService.resolve(userId);
        List<AlarmRow> rows = alarmRecordService.listNewAlarms(scope, sinceId,
                LocalDateTime.now().minusMinutes(lookback), NOTIFY_LIMIT);
        return rows.stream().map(AlarmViewConverter::toNotificationVO).toList();
    }

    @Override
    public void process(Long userId, String userName, Long alarmId, String remark) {
        if (alarmId == null || alarmId <= 0) {
            throw new CmBusinessException("报警 ID 不能为空");
        }
        requireRemark(remark);
        DataScope scope = dataScopeService.resolve(userId);
        alarmRecordService.process(scope, alarmId, userId, userName, PROCESS_RESULT, remark);
        log.info("报警已处理, userId={}, alarmId={}", userId, alarmId);
    }

    @Override
    public AlarmProcessResultVO processBatch(Long userId, String userName, List<Long> alarmIds,
                                             String remark) {
        if (alarmIds == null || alarmIds.isEmpty()) {
            throw new CmBusinessException("请选择要处理的报警记录");
        }
        requireRemark(remark);
        // 去重但保留页面勾选顺序：重复 ID 不该被处理两次，也不该让 skipped 里出现两条同因记录。
        LinkedHashSet<Long> ids = new LinkedHashSet<>(alarmIds);
        if (ids.size() > MAX_BATCH) {
            throw new CmBusinessException("单次批量处理不能超过 " + MAX_BATCH + " 条");
        }
        DataScope scope = dataScopeService.resolve(userId);
        int processed = 0;
        List<AlarmSkipVO> skipped = new ArrayList<>();
        for (Long alarmId : ids) {
            if (alarmId == null || alarmId <= 0) {
                skipped.add(new AlarmSkipVO(String.valueOf(alarmId), "报警 ID 不合法"));
                continue;
            }
            try {
                alarmRecordService.process(scope, alarmId, userId, userName, PROCESS_RESULT, remark);
                processed++;
            } catch (CmBusinessException ex) {
                skipped.add(new AlarmSkipVO(String.valueOf(alarmId), ex.getMessage()));
            }
        }
        log.info("报警批量处理完成, userId={}, 成功={}, 跳过={}", userId, processed, skipped.size());
        return new AlarmProcessResultVO(processed, skipped);
    }

    private void requireTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new CmBusinessException("开始时间与结束时间必填");
        }
        if (startTime.isAfter(endTime)) {
            throw new CmBusinessException("开始时间不能晚于结束时间");
        }
    }

    private void requireRemark(String remark) {
        if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
            throw new CmBusinessException("处理备注不能超过 " + MAX_REMARK_LENGTH + " 字符");
        }
    }
}
