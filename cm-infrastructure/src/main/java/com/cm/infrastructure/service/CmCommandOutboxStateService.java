package com.cm.infrastructure.service;

import com.cm.infrastructure.persistence.entity.CmCommandOutbox;

import java.util.OptionalInt;

/**
 * Outbox 状态推进。所有迁移都是条件更新，旧回调与重复回调不会覆盖新状态。
 */
public interface CmCommandOutboxStateService {

    /**
     * 本次发布已发起：PENDING → PUBLISHED，retry_count 兼作发布尝试序号。
     *
     * @return 本次发布尝试序号；被其他实例抢走时返回空
     */
    OptionalInt markPublished(CmCommandOutbox row, String exchangeName, String routingKey, String payload);

    /**
     * confirm=ack 且未 return：PUBLISHED → CONFIRMED，并把指令推进到已发 MQ。
     */
    void confirm(String commandId, int publishAttempt);

    /**
     * nack、return 或 confirm 超时：按退避回到 PENDING；尝试耗尽则 FAILED 并落死信。
     */
    void fail(String commandId, int publishAttempt, String reason);

    /**
     * 尚未发布就退避（例如目标终端没有可用路由），尝试次数同样递增。
     */
    void defer(CmCommandOutbox row, String reason);

    /**
     * 指令已进入终态，不再发布。
     */
    void skip(CmCommandOutbox row);

    /**
     * 回收超过租约仍处于 PUBLISHED 的记录，返回处理条数。
     */
    int recoverExpired(int batchSize);
}
