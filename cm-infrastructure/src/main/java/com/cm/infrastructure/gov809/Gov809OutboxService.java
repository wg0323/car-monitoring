package com.cm.infrastructure.gov809;

import com.cm.infrastructure.persistence.entity.CmGov809Outbox;

import java.util.List;

/**
 * 809 断链积压与补发队列。定位按 event_seq 排序补发，非定位（督办应答等）逐条补发；
 * biz_event_id 唯一，MQ 重复投递与重连重试都不会把同一事件入队两次。
 */
public interface Gov809OutboxService {

    /** 返回 false 表示该业务事件已入过队，调用方无需处理。 */
    boolean append(Gov809OutboxMessage message);

    List<CmGov809Outbox> claimPending(long platformId, int limit);

    void markSent(List<Long> ids);

    void markFailed(long id, String error);

    long countPending(long platformId);

    /** 链路已分配的最大序号，重启时据此续号。 */
    long maxAllocatedSeq(long platformId);

    /** 已写入链路的最后一个序号，写检查点用。 */
    long maxSentSeq(long platformId);
}
 
