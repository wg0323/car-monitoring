package com.cm.infrastructure.command;

import lombok.Getter;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 发布确认的定位信息：业务事件 ID 与本次发布尝试次数。
 * 回调只处理这一类型，死信重放等没有业务归属的发布会使用普通 CorrelationData，不会误改 Outbox 状态。
 */
@Getter
public class CommandPublishCorrelationData extends CorrelationData {

    private final String commandId;

    private final int publishAttempt;

    public CommandPublishCorrelationData(String commandId, int publishAttempt) {
        super(commandId);
        this.commandId = commandId;
        this.publishAttempt = publishAttempt;
    }
}
