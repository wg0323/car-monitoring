package com.cm.gateway.telemetry;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.infrastructure.telemetry.TelemetryPublishTracker;
import com.cm.infrastructure.telemetry.TelemetryPublisher;
import com.cm.protocol.Jt808Encoder;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.IntSupplier;

/**
 * 定位上报的发布与应答编排。EventLoop 只负责组装，发布与等待 confirm 放到业务线程池；
 * 只有整批 confirm=ack 且无 return 才回到 EventLoop 写 0x8001，超时或失败一律不应答，
 * 由终端重发同一帧得到同一 eventId，消费端据此去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionReportDispatcher {

    private static final int SERIAL_MASK = 0xFFFF;

    private final TelemetryPublisher publisher;

    private final TelemetryProperties properties;

    private final TelemetryMetrics metrics;

    private final ThreadPoolExecutor telemetryPublishExecutor;

    public void dispatch(Channel channel, String simNo, int ackMessageId, int terminalSerialNo,
            List<PositionEvent> events, boolean ackEnabled, IntSupplier platformSerialSupplier) {
        try {
            telemetryPublishExecutor.execute(() -> publishAndAck(channel, simNo, ackMessageId,
                    terminalSerialNo, events, ackEnabled, platformSerialSupplier));
        } catch (RejectedExecutionException ex) {
            metrics.publishFailed();
            log.error("定位发布线程池已满，本次不应答等待终端重报, simNo={}, serialNo={}, count={}",
                    simNo, terminalSerialNo, events.size());
        }
    }

    private void publishAndAck(Channel channel, String simNo, int ackMessageId, int terminalSerialNo,
            List<PositionEvent> events, boolean ackEnabled, IntSupplier platformSerialSupplier) {
        TelemetryPublishTracker tracker = publisher.publishBatch(events);
        metrics.published(events.size());
        boolean confirmed = tracker.await(properties.getPublish().getConfirmTimeoutMillis());
        if (!confirmed) {
            if (tracker.failed()) {
                metrics.publishFailed();
            } else {
                metrics.ackTimeout();
            }
            log.warn("定位消息未获确认，不回应答, simNo={}, serialNo={}, ackMessageId={}, count={}, failed={}",
                    simNo, terminalSerialNo, ackMessageId, events.size(), tracker.failed());
            return;
        }
        if (!ackEnabled || !channel.isActive()) {
            return;
        }
        int platformSerialNo = platformSerialSupplier.getAsInt() & SERIAL_MASK;
        byte[] ack = Jt808Encoder.successAck(simNo, platformSerialNo, terminalSerialNo, ackMessageId);
        channel.eventLoop().execute(() -> channel.writeAndFlush(ack));
        metrics.ackSent();
    }
}
