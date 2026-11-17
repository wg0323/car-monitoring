package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.mapper.CmCommandOutboxMapper;
import com.cm.infrastructure.service.CmCommandOutboxLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租约抢占实现。SELECT ... FOR UPDATE SKIP LOCKED 与写租约在同一事务内完成，
 * 同一行只可能被一个实例拿到；其他实例跳过被锁定的行，继续抢后面的记录，不会等待。
 */
@Service
@RequiredArgsConstructor
public class CmCommandOutboxLeaseServiceImpl implements CmCommandOutboxLeaseService {

    private final CmCommandOutboxMapper outboxMapper;

    private final CmCommandProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CmCommandOutbox> claim(String owner) {
        List<CmCommandOutbox> rows = outboxMapper.selectClaimable(properties.getRelay().getBatchSize());
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(CmCommandOutbox::getId).toList();
        outboxMapper.leaseByIds(ids, owner, properties.getRelay().getLeaseSeconds());
        return rows;
    }
}
