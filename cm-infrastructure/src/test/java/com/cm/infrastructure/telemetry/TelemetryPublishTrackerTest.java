package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布确认聚合：全部 ack 才算成功，任一 nack / return 立即失败，迟到回调不改变结论。
 */
class TelemetryPublishTrackerTest {

    private static final long SHORT_TIMEOUT_MILLIS = 100L;

    private static final long TIMEOUT_MILLIS = 50L;

    @Test
    @DisplayName("整批 ack 后成功")
    void should_succeed_when_all_messages_acked() {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(2);
        tracker.onAck();
        tracker.onAck();
        assertTrue(tracker.await(SHORT_TIMEOUT_MILLIS));
        assertFalse(tracker.failed());
    }

    @Test
    @DisplayName("任一消息 nack 后整批失败，剩余 ack 不影响结论")
    void should_fail_when_any_message_fails() {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(3);
        tracker.onAck();
        tracker.onFailure();
        tracker.onAck();
        assertFalse(tracker.await(SHORT_TIMEOUT_MILLIS));
        assertTrue(tracker.failed());
    }

    @Test
    @DisplayName("确认超时按失败处理")
    void should_fail_when_timeout() {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(2);
        tracker.onAck();
        assertFalse(tracker.await(TIMEOUT_MILLIS));
    }

    @Test
    @DisplayName("失败后迟到的 ack 不会把结果改回成功")
    void should_ignore_late_ack_after_failure() {
        TelemetryPublishTracker tracker = TelemetryPublishTracker.of(2);
        tracker.onFailure();
        tracker.onAck();
        tracker.onAck();
        assertFalse(tracker.await(TIMEOUT_MILLIS));
    }
}
