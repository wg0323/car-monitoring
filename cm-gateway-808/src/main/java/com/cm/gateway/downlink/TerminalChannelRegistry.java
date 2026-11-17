package com.cm.gateway.downlink;

import com.cm.transport.ChannelRoute;
import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关本地连接表。Channel 本体只存在网关内存里，Redis 只保存路由。
 * incarnation 在 channelActive 时分配，用于区分同名 SIM 的新旧连接。
 */
@Component
public class TerminalChannelRegistry {

    private static final int SERIAL_MASK = 0xFFFF;

    private final ConcurrentMap<String, TerminalConnection> connections = new ConcurrentHashMap<>();

    private final AtomicLong incarnations = new AtomicLong(System.currentTimeMillis());

    public long nextIncarnation() {
        return incarnations.incrementAndGet();
    }

    /**
     * 同一连接重新鉴权时沿用原流水号计数器，避免从 1 重新开始而撞上历史尝试。
     */
    public void bind(ChannelRoute route, Channel channel) {
        TerminalConnection existing = connections.get(route.simNo());
        AtomicInteger serialNo = existing != null && existing.channel() == channel
                ? existing.serialNo()
                : new AtomicInteger();
        connections.put(route.simNo(), new TerminalConnection(route, channel, serialNo));
    }

    public Optional<TerminalConnection> find(String simNo) {
        return Optional.ofNullable(connections.get(simNo));
    }

    /**
     * 只有当前连接自己的路由才能解绑，旧连接的延迟关闭不会删掉新连接。
     */
    public boolean unbind(ChannelRoute expected) {
        TerminalConnection current = connections.get(expected.simNo());
        if (current == null) {
            return false;
        }
        return current.route().sameConnection(expected) && connections.remove(expected.simNo(), current);
    }

    public int size() {
        return connections.size();
    }

    /**
     * 一条连接上的下行消息流水号，按 16 位回绕。
     */
    public record TerminalConnection(ChannelRoute route, Channel channel, AtomicInteger serialNo) {

        public int nextSerialNo() {
            return serialNo.incrementAndGet() & SERIAL_MASK;
        }

        public boolean active() {
            return channel.isActive();
        }
    }
}
