package com.cm.gateway.telemetry;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.infrastructure.telemetry.TelemetryPublishTracker;
import com.cm.infrastructure.telemetry.TelemetryPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 应答时序：只有整批 confirm 成功才写 0x8001；失败、超时与私有帧都不写。
 */
class PositionReportDispatcherTest {

    private static final String SIM_NO = "13800138000";

    private static final long AWAIT_SECONDS = 2L;

    private TelemetryPublisher publisher;

    private ThreadPoolExecutor executor;

    private PositionReportDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        publisher = mock(TelemetryPublisher.class);
        TelemetryProperties properties = new TelemetryProperties();
        properties.getPublish().setConfirmTimeoutMillis(100L);
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        dispatcher = new PositionReportDispatcher(publisher, properties,
                new TelemetryMetrics(new SimpleMeterRegistry()), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("整批确认成功后写 0x8001")
    void should_reply_ack_after_confirm() throws InterruptedException {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(1);
        tracker.onAck();
        when(publisher.publishBatch(anyList())).thenReturn(tracker);
        EmbeddedChannel channel = new EmbeddedChannel();
        dispatcher.dispatch(channel, SIM_NO, 0x0200, 7, List.of(event()), true, () -> 9);
        awaitTasks(channel);
        assertNotNull(channel.readOutbound(), "确认成功后必须回应答");
    }

    @Test
    @DisplayName("确认失败不回应答")
    void should_not_reply_after_failure() throws InterruptedException {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(1);
        tracker.onFailure();
        when(publisher.publishBatch(anyList())).thenReturn(tracker);
        EmbeddedChannel channel = new EmbeddedChannel();
        dispatcher.dispatch(channel, SIM_NO, 0x0200, 7, List.of(event()), true, () -> 9);
        awaitTasks(channel);
        assertNull(channel.readOutbound(), "确认失败不能回应答");
    }

    @Test
    @DisplayName("确认超时不回应答")
    void should_not_reply_when_timeout() throws InterruptedException {
        when(publisher.publishBatch(anyList())).thenReturn(TelemetryPublishTracker.of(1));
        EmbeddedChannel channel = new EmbeddedChannel();
        dispatcher.dispatch(channel, SIM_NO, 0x0200, 7, List.of(event()), true, () -> 9);
        awaitTasks(channel);
        assertNull(channel.readOutbound(), "超时不能回应答，交给终端重发");
    }

    @Test
    @DisplayName("私有帧确认成功后也不回应答")
    void should_not_reply_for_private_frame() throws InterruptedException {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(1);
        tracker.onAck();
        when(publisher.publishBatch(anyList())).thenReturn(tracker);
        EmbeddedChannel channel = new EmbeddedChannel();
        dispatcher.dispatch(channel, SIM_NO, 0x0200, 7, List.of(event()), false, () -> 9);
        awaitTasks(channel);
        assertNull(channel.readOutbound(), "私有协议没有标准应答");
    }

    private void awaitTasks(EmbeddedChannel channel) throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);
        channel.runPendingTasks();
    }

    private PositionEvent event() {
        PositionSample sample = new PositionSample(0, 1, 30000000, 120000000, 0, 60, 90, 100L, 0, 0, 0, 0);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, "E1", PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, SIM_NO, 7, Instant.parse("2026-11-15T04:00:00Z").toString(),
                Instant.parse("2026-11-15T04:00:02Z").toString(),
                Instant.parse("2026-11-15T04:00:00Z").toString(), "NORMAL", "gw-1", "E1", 0, sample,
                "abcdef0123456789");
    }
}
