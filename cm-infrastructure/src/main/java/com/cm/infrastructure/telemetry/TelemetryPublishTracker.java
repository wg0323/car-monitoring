package com.cm.infrastructure.telemetry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一批定位消息的发布确认聚合。一帧批量定位会拆成多条消息，
 * 只有全部 confirm=ack 且没有任何一条 return，这批才算被 Broker 可靠受理。
 * 任一失败立即结束等待，迟到回调不再改变结果。
 */
public final class TelemetryPublishTracker {

    private final int expected;

    private final CountDownLatch latch;

    private final AtomicInteger remaining;

    private final AtomicBoolean failed;

    private final AtomicBoolean finished;

    private TelemetryPublishTracker(int expected) {
        if (expected <= 0) {
            throw new IllegalArgumentException("expected must be positive");
        }
        this.expected = expected;
        this.latch = new CountDownLatch(1);
        this.remaining = new AtomicInteger(expected);
        this.failed = new AtomicBoolean(false);
        this.finished = new AtomicBoolean(false);
    }

    public static TelemetryPublishTracker of(int expected) {
        return new TelemetryPublishTracker(expected);
    }

    /**
     * 单条消息收到 confirm=ack 且未被 return。
     */
    public void onAck() {
        if (remaining.decrementAndGet() <= 0) {
            finish();
        }
    }

    /**
     * 单条消息 nack 或 return。整批不再可能成功。
     */
    public void onFailure() {
        failed.set(true);
        finish();
    }

    /**
     * 等待整批结果。超时、线程中断、任一失败都返回 false，调用方据此决定是否回协议应答。
     */
    public boolean await(long timeoutMillis) {
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !failed.get() && remaining.get() == 0;
    }

    public int expected() {
        return expected;
    }

    public boolean failed() {
        return failed.get();
    }

    /**
     * 只释放一次等待闸门，宁晚不重。
     */
    private void finish() {
        if (finished.compareAndSet(false, true)) {
            latch.countDown();
        }
    }
}
