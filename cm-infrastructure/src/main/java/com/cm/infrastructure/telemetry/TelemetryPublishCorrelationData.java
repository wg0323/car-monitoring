package com.cm.infrastructure.telemetry;

import lombok.Getter;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 发布确认的定位信息。回调只处理这一类型，其他链路的发布会用普通 CorrelationData，
 * 不会被误判为定位事件。
 */
@Getter
public class TelemetryPublishCorrelationData extends CorrelationData {

    private final TelemetryPublishTracker tracker;

    public TelemetryPublishCorrelationData(String eventId, TelemetryPublishTracker tracker) {
        super(eventId);
        this.tracker = tracker;
    }
}
