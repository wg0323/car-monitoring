package com.cm.infrastructure.service;

/**
 * Outbox 转发任务。只在管理端进程开启，网关闭。
 */
public interface CmCommandOutboxRelayService {

    /**
     * 抢占一批记录并尝试发布一次，返回实际发起的发布条数。
     */
    int relayOnce();
}
