package com.cm.transport;

import com.cm.protocol.jt809.Jt809FrameCodec;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一条 809 链路的连接与本端流水号。主链路（本端连出）与从链路（对端连入）分开登记，
 * 发送优先主链路、失败回退从链路，与旧实现 Send 的降级顺序一致。
 * 断开时只有当前登记的 Channel 才能清状态，避免旧连接的迟到关闭事件误清新连接。
 */
public final class Jt809LinkSession {

    private static final int SN_MASK = 0xFFFFFFFF;

    private final long centerId;

    private final AtomicInteger serialNo = new AtomicInteger();

    private volatile Channel mainChannel;

    private volatile Channel subChannel;

    private volatile boolean mainLoggedIn;

    private volatile boolean subLoggedIn;

    public Jt809LinkSession(long centerId) {
        this.centerId = centerId;
    }

    public long centerId() {
        return centerId;
    }

    public int nextSerialNo() {
        return serialNo.incrementAndGet() & SN_MASK;
    }

    public byte[] encode(int messageId, byte[] body) {
        return Jt809FrameCodec.encode(messageId, nextSerialNo(), centerId, body);
    }

    /**
     * 登记主链路。同一平台再次登录时先关闭旧连接：
     * 旧连接若仍在网上，本端会同时维护两条主链路，序号与状态都不再唯一。
     */
    public void bindMain(Channel channel) {
        Channel previous = mainChannel;
        mainChannel = channel;
        mainLoggedIn = false;
        closeQuietly(previous, channel);
    }

    public void bindSub(Channel channel) {
        Channel previous = subChannel;
        subChannel = channel;
        subLoggedIn = false;
        closeQuietly(previous, channel);
    }

    public void unbind(Channel channel) {
        if (channel == mainChannel) {
            mainChannel = null;
            mainLoggedIn = false;
        }
        if (channel == subChannel) {
            subChannel = null;
            subLoggedIn = false;
        }
    }

    public void markMainLoggedIn(boolean loggedIn) {
        mainLoggedIn = loggedIn;
    }

    public void markSubLoggedIn(boolean loggedIn) {
        subLoggedIn = loggedIn;
    }

    public boolean isMainActive() {
        Channel channel = mainChannel;
        return channel != null && channel.isActive();
    }

    public boolean isSubActive() {
        Channel channel = subChannel;
        return channel != null && channel.isActive();
    }

    public boolean isMainLoggedIn() {
        return isMainActive() && mainLoggedIn;
    }

    public boolean isSubLoggedIn() {
        return isSubActive() && subLoggedIn;
    }

    /** 登录完成后的链路才允许发业务报文。 */
    public boolean hasUsableLink() {
        return isMainActive() && mainLoggedIn || isSubActive() && subLoggedIn;
    }

    public boolean writeMain(byte[] frame, long timeoutMillis) {
        return write(mainChannel, frame, timeoutMillis);
    }

    public boolean writeSub(byte[] frame, long timeoutMillis) {
        return write(subChannel, frame, timeoutMillis);
    }

    public boolean writeAny(byte[] frame, long timeoutMillis) {
        if (isMainActive() && mainLoggedIn && write(mainChannel, frame, timeoutMillis)) {
            return true;
        }
        return isSubActive() && subLoggedIn && write(subChannel, frame, timeoutMillis);
    }

    /** 应答类写入：调用方在 EventLoop 内，不能等待写结果，直接排入队列。 */
    public static void writeNoWait(Channel channel, byte[] frame) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(frame);
        }
    }

    private static void closeQuietly(Channel previous, Channel current) {
        if (previous != null && previous != current && previous.isActive()) {
            previous.close();
        }
    }

    private static boolean write(Channel channel, byte[] frame, long timeoutMillis) {
        if (channel == null || !channel.isActive()) {
            return false;
        }
        ChannelFuture future = channel.writeAndFlush(frame);
        // 调用方在消费线程而非 EventLoop，短暂等待写结果可接受；EventLoop 内部禁用本方法。
        future.awaitUninterruptibly(timeoutMillis, TimeUnit.MILLISECONDS);
        return future.isSuccess();
    }
}

