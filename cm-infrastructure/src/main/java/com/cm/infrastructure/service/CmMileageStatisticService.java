package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.infrastructure.persistence.entity.CmMileageStatistic;

/**
 * 里程统计写入。只有定位链路的定时任务调用它。
 */
public interface CmMileageStatisticService extends IService<CmMileageStatistic> {

    /**
     * 按 (period_type, plate_no, statistic_date) 写入当日绝对值，重复调用结果一致。
     */
    void upsertDaily(CmMileageStatistic record);
}
