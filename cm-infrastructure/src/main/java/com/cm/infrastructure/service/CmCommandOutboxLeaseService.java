package com.cm.infrastructure.service;

import com.cm.infrastructure.persistence.entity.CmCommandOutbox;

import java.util.List;

/**
 * Outbox 租约抢占。抢不到立即返回空集合，不阻塞调用线程。
 */
public interface CmCommandOutboxLeaseService {

    /**
     * 抢占一批可发布记录并写入租约，返回的记录已归属 owner。
     */
    List<CmCommandOutbox> claim(String owner);
}
