package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 指令 Outbox Mapper。租约抢占与状态推进写在 mapper/CommandOutboxMapper.xml。
 * 抢占用 SELECT ... FOR UPDATE SKIP LOCKED，抢不到立即返回，不阻塞其他实例。
 */
public interface CmCommandOutboxMapper extends BaseMapper<CmCommandOutbox> {

    /**
     * 抢占一批可发布记录，调用方必须在事务内使用并紧接着 leaseByIds。
     */
    List<CmCommandOutbox> selectClaimable(@Param("size") int size);

    CmCommandOutbox selectByCommandId(@Param("commandId") String commandId);

    int leaseByIds(@Param("ids") List<Long> ids,
                   @Param("owner") String owner,
                   @Param("leaseSeconds") int leaseSeconds);

    /**
     * 标记本次发布已发起；publishAttempt 作为乐观条件，防止旧尝试覆盖新状态。
     */
    int markPublished(@Param("id") Long id,
                      @Param("previousAttempt") int previousAttempt,
                      @Param("publishAttempt") int publishAttempt,
                      @Param("exchangeName") String exchangeName,
                      @Param("routingKey") String routingKey,
                      @Param("payload") String payload,
                      @Param("confirmTimeoutSeconds") int confirmTimeoutSeconds);

    int markConfirmed(@Param("id") Long id, @Param("publishAttempt") int publishAttempt);

    int markPendingRetry(@Param("id") Long id,
                         @Param("publishAttempt") int publishAttempt,
                         @Param("delayMillis") long delayMillis);

    int markFailed(@Param("id") Long id, @Param("publishAttempt") int publishAttempt);

    /**
     * 未发布就退避重试（例如无路由、目标网关无连接），并推高尝试次数。
     */
    int markDeferred(@Param("id") Long id,
                     @Param("previousAttempt") int previousAttempt,
                     @Param("publishAttempt") int publishAttempt,
                     @Param("delayMillis") long delayMillis);

    int markSkipped(@Param("id") Long id, @Param("publishAttempt") int publishAttempt);

    /**
     * 回收超过租约仍处于 PUBLISHED 的记录：confirm 没有到达，结果未知，按可重试处理。
     */
    List<CmCommandOutbox> selectExpiredPublished(@Param("size") int size);

    int releaseLease(@Param("id") Long id, @Param("publishAttempt") int publishAttempt);
}
