package com.cm.infrastructure.service;

/**
 * 指令消息发布。发布方只负责把已持久化的消息交给 Broker，
 * 成功与否由 confirm/return 回调判定，方法返回不代表已入队。
 */
public interface CmCommandPublisher {

    /**
     * 发布下行事件；messageId 固定为 commandId，重发不变。
     */
    void publishDownlink(String commandId, int publishAttempt, String exchange, String routingKey, String payload);

    /**
     * 死信重放，不携带 Outbox 定位信息。
     */
    void replay(String exchange, String routingKey, String messageId, String payload);
}
