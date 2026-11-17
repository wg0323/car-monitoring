package com.cm.integration.forward;

import com.cm.infrastructure.gov809.Gov809OutboxMessage;
import com.cm.infrastructure.gov809.Gov809OutboxService;
import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.TelemetryJson;
import com.cm.integration.config.Gov809Properties;
import com.cm.integration.link.Gov809LinkManager;
import com.cm.protocol.jt809.Jt809Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

/**
 * 定位事件转 809。链路可用时直写；不可用时把已编码的消息体写入积压队列，
 * 重连后由补发服务按序重发，不在内存里堆队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Gov809ForwardHandler {

    private final Gov809FrameBuilder frameBuilder;

    private final Gov809LinkManager linkManager;

    private final Gov809OutboxService outboxService;

    private final Gov809Properties properties;

    public void handle(String payload) {
        PositionEvent event = TelemetryJson.read(payload, PositionEvent.class);
        if (!PositionEvent.EVENT_TYPE.equals(event.eventType())) {
            log.debug("非定位事件不进 809 链路, eventType={}", event.eventType());
            return;
        }
        Gov809FrameBuilder.BuiltFrame frame = frameBuilder.build(event);
        if (frame == null) {
            log.debug("车辆未绑定车牌，跳过 809 转发, simNo={}", event.simNo());
            return;
        }
        if (linkManager.send(Jt809Constants.MSG_VEHICLE_LOCATION, frame.body())) {
            return;
        }
        outboxService.append(new Gov809OutboxMessage(properties.getPlatformId(), event.eventId(),
                Jt809Constants.MSG_VEHICLE_LOCATION, Jt809Constants.SUB_TYPE_REAL_LOCATION,
                frame.plateNo(), frame.plateColor(), HexFormat.of().formatHex(frame.body())));
        log.info("809 链路不可用，定位已入积压队列, simNo={}, eventId={}", event.simNo(), event.eventId());
    }
}
 
