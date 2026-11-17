package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.infrastructure.persistence.entity.CmMileageStatistic;
import com.cm.infrastructure.persistence.mapper.CmMileageStatisticMapper;
import com.cm.infrastructure.service.CmMileageStatisticService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 里程统计写入实现。UPSERT 走原生 SQL，主键仍由同一个雪花生成器写入，不能用自增。
 */
@Service
@RequiredArgsConstructor
public class CmMileageStatisticServiceImpl extends ServiceImpl<CmMileageStatisticMapper, CmMileageStatistic>
        implements CmMileageStatisticService {

    private final IdentifierGenerator identifierGenerator;

    @Override
    public void upsertDaily(CmMileageStatistic record) {
        if (record.getId() == null) {
            record.setId(identifierGenerator.nextId(record).longValue());
        }
        baseMapper.upsertDaily(record);
    }
}
