package com.cm.infrastructure.service;

import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import org.springframework.amqp.core.Message;

/**
 * 死信持久化与受控重放。只声明 DLQ 不落库会丢证据，因此死信一律先入表。
 */
public interface CmMqDeadLetterService {

    /**
     * 发布重试耗尽时保留 payload 与路由，供修复后重放。
     */
    void parkOutboxFailure(CmCommandOutbox row, int publishAttempt, String reason);

    /**
     * DLQ 消费者收到的死信落库，幂等。
     */
    void parkDeadMessage(Message message, String sourceQueue, String reason);

    /**
     * 按记录重放一次；同一记录并发重放只有一次成功。
     */
    boolean replay(Long id);
}
