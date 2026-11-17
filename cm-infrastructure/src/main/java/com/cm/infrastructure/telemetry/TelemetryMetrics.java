package com.cm.infrastructure.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 定位链路指标。指标名集中在这里，避免各处手写字符串；
 * 消费者只做加计数，不做日志替代。
 */
@Component
@RequiredArgsConstructor
public class TelemetryMetrics {

    private static final String PUBLISHED = "cm.telemetry.published";

    private static final String PUBLISH_FAILED = "cm.telemetry.publish.failed";

    private static final String ACK_SENT = "cm.telemetry.ack.sent";

    private static final String ACK_TIMEOUT = "cm.telemetry.ack.timeout";

    private static final String DEDUP_HIT = "cm.telemetry.dedup.hit";

    private static final String HISTORY_SAVED = "cm.telemetry.history.saved";

    private static final String QUARANTINED = "cm.telemetry.quarantined";

    private static final String LATEST_UPDATED = "cm.telemetry.latest.updated";

    private static final String LATEST_STALE = "cm.telemetry.latest.stale";

    private static final String BROADCAST_SENT = "cm.telemetry.broadcast.sent";

    private static final String BROADCAST_FAILED = "cm.telemetry.broadcast.failed";

    private static final String ONLINE = "cm.telemetry.online";

    private static final String OFFLINE = "cm.telemetry.offline";

    private static final String LEASE_RENEWED = "cm.telemetry.lease.renewed";

    private static final String LEASE_RECONCILED = "cm.telemetry.lease.reconciled";

    private static final String ALARM_SKIPPED = "cm.telemetry.alarm.skipped";

    private static final String ALARM_OPENED = "cm.telemetry.alarm.opened";

    private static final String ALARM_CLOSED = "cm.telemetry.alarm.closed";

    private static final String ALARM_DUPLICATE = "cm.telemetry.alarm.duplicate";

    private static final String ALARM_CLOSE_MISSED = "cm.telemetry.alarm.close.missed";

    private static final String MILEAGE_COUNTED = "cm.telemetry.mileage.counted";

    private static final String MILEAGE_DISCARDED = "cm.telemetry.mileage.discarded";

    private static final String MILEAGE_FLUSHED = "cm.telemetry.mileage.flushed";

    private static final String REGISTRATION_ACCEPTED = "cm.gateway.registration.accepted";

    private static final String REGISTRATION_REJECTED = "cm.gateway.registration.rejected";

    private static final String AUTH_REJECTED = "cm.gateway.auth.rejected";

    private static final String DEREGISTERED = "cm.gateway.deregistered";

    private final MeterRegistry meterRegistry;

    public void published() {
        meterRegistry.counter(PUBLISHED).increment();
    }

    public void published(int count) {
        meterRegistry.counter(PUBLISHED).increment(count);
    }

    public void publishFailed() {
        meterRegistry.counter(PUBLISH_FAILED).increment();
    }

    public void ackSent() {
        meterRegistry.counter(ACK_SENT).increment();
    }

    public void ackTimeout() {
        meterRegistry.counter(ACK_TIMEOUT).increment();
    }

    public void dedupHit() {
        meterRegistry.counter(DEDUP_HIT).increment();
    }

    public void historySaved() {
        meterRegistry.counter(HISTORY_SAVED).increment();
    }

    public void quarantined() {
        meterRegistry.counter(QUARANTINED).increment();
    }

    public void latestUpdated() {
        meterRegistry.counter(LATEST_UPDATED).increment();
    }

    public void latestStale() {
        meterRegistry.counter(LATEST_STALE).increment();
    }

    public void broadcastSent() {
        meterRegistry.counter(BROADCAST_SENT).increment();
    }

    public void broadcastFailed() {
        meterRegistry.counter(BROADCAST_FAILED).increment();
    }

    public void online() {
        meterRegistry.counter(ONLINE).increment();
    }

    public void offline() {
        meterRegistry.counter(OFFLINE).increment();
    }

    public void leaseRenewed() {
        meterRegistry.counter(LEASE_RENEWED).increment();
    }

    public void leaseReconciled() {
        meterRegistry.counter(LEASE_RECONCILED).increment();
    }

    public void alarmSkipped() {
        meterRegistry.counter(ALARM_SKIPPED).increment();
    }

    public void alarmOpened() {
        meterRegistry.counter(ALARM_OPENED).increment();
    }

    public void alarmClosed() {
        meterRegistry.counter(ALARM_CLOSED).increment();
    }

    public void alarmDuplicate() {
        meterRegistry.counter(ALARM_DUPLICATE).increment();
    }

    public void alarmCloseMissed() {
        meterRegistry.counter(ALARM_CLOSE_MISSED).increment();
    }

    public void mileageCounted() {
        meterRegistry.counter(MILEAGE_COUNTED).increment();
    }

    /**
     * 重置、跳变过大与无基线都归到这里，它们都是“本次不计入里程”的情况。
     */
    public void mileageDiscarded(String reason) {
        meterRegistry.counter(MILEAGE_DISCARDED, "reason", reason).increment();
    }

    public void mileageFlushed(int count) {
        meterRegistry.counter(MILEAGE_FLUSHED).increment(count);
    }

    public void registrationAccepted() {
        meterRegistry.counter(REGISTRATION_ACCEPTED).increment();
    }

    public void registrationRejected() {
        meterRegistry.counter(REGISTRATION_REJECTED).increment();
    }

    /**
     * 鉴权码不匹配。与 registration 分开统计：一个是「这台设备不该接入」，一个是「码不对」。
     */
    public void authRejected() {
        meterRegistry.counter(AUTH_REJECTED).increment();
    }

    public void deregistered() {
        meterRegistry.counter(DEREGISTERED).increment();
    }
}
