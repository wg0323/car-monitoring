package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmMileageStatistic;
import org.apache.ibatis.annotations.Param;

/**
 * 里程统计 Mapper。写入是「当日绝对值覆盖」的 UPSERT，重复执行结果相同。
 */
public interface CmMileageStatisticMapper extends BaseMapper<CmMileageStatistic> {

    int upsertDaily(@Param("record") CmMileageStatistic record);
}
