package com.cm.infrastructure.telemetry;

import com.cm.domain.position.TimeQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 报警位消费。与最新位置一样跳过异常时间事件：
 * 未来时间或过老时间的报文本就是脏数据，不能让它把报警的开 / 关状态带偏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryAlarmHandler {

    private static final String FUTURE = TimeQuality.FUTURE.name();

    private static final String STALE = TimeQuality.STALE.name();

    private final AlarmRecordWriter writer;

    private final VehicleBindingResolver bindingResolver;

    private final TelemetryMetrics metrics;

    public void handle(String payload) {
        PositionEvent event = TelemetryJson.read(payload, PositionEvent.class);
        if (FUTURE.equals(event.timeQuality()) || STALE.equals(event.timeQuality())) {
            metrics.alarmSkipped();
            return;
        }
        writer.apply(event, bindingResolver.resolve(event.simNo()));
    }
}
