package com.cm.infrastructure.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 定位消息的发布确认回调。ack 且未发生 return 才把该条计入成功；
 * return 已发生的消息随后仍可能收到 ack，靠 CorrelationData 上的 returned 标记挡住。
 * 回调只改内存计数，不做重发，重发由终端超时重报驱动。
 */
@Slf4j
@Component
public class TelemetryPublishCallback implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!(correlationData instanceof TelemetryPublishCorrelationData data)) {
            return;
        }
        if (ack && data.getReturned() == null) {
            data.getTracker().onAck();
            return;
        }
        log.warn("定位消息未被 Broker 受理, eventId={}, ack={}, cause={}", data.getId(), ack, cause);
        data.getTracker().onFailure();
    }

    /**
     * 不可路由时先产生 return 再收到 ack，这里只记录；判定统一在 confirm 回调里做。
     */
    @Override
    public void returnedMessage(ReturnedMessage returned) {
        log.warn("定位消息未路由到队列, eventId={}, exchange={}, routingKey={}, replyCode={}",
                returned.getMessage().getMessageProperties().getMessageId(),
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode());
    }
}
