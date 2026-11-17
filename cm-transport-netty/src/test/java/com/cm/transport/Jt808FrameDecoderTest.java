package com.cm.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 切帧的边界行为：整帧、粘包、半包、前后噪声、连续标识位、$ 私有帧与超长噪声。
 */
class Jt808FrameDecoderTest {

    private static final byte[] HEARTBEAT = hex("7E0002000001588002223915885F7E");

    private static final byte[] AUTH = hex("7E0102000B01588002223915883132333435363738393041157E");

    private static final byte[] PRIVATE_FRAME = hex(
            "2401391234560930151411262605523300119232195006003000000400000000");

    @Test
    @DisplayName("单帧输入产出一条完整帧")
    void singleFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(HEARTBEAT));
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        assertNull(channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("两帧粘包一次产出两条帧")
    void stickyPackets() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        byte[] buffer = new byte[HEARTBEAT.length + AUTH.length];
        System.arraycopy(HEARTBEAT, 0, buffer, 0, HEARTBEAT.length);
        System.arraycopy(AUTH, 0, buffer, HEARTBEAT.length, AUTH.length);
        channel.writeInbound(Unpooled.wrappedBuffer(buffer));
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        assertArrayEquals(AUTH, channel.readInbound());
        assertNull(channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("逐字节到达的半包只在收齐后产出一帧")
    void halfPacketByteByByte() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        for (int index = 0; index < HEARTBEAT.length; index++) {
            channel.writeInbound(Unpooled.wrappedBuffer(new byte[] { HEARTBEAT[index] }));
            if (index < HEARTBEAT.length - 1) {
                assertNull(channel.readInbound(), "第 " + index + " 字节后不应产出帧");
            }
        }
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("帧前噪声被丢弃，不污染首帧")
    void leadingGarbageIsDropped() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        byte[] buffer = new byte[HEARTBEAT.length + 5];
        Arrays.fill(buffer, 0, 5, (byte) 0x11);
        System.arraycopy(HEARTBEAT, 0, buffer, 5, HEARTBEAT.length);
        channel.writeInbound(Unpooled.wrappedBuffer(buffer));
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        assertNull(channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("帧前多出一个标识位时不产出空帧")
    void consecutiveDelimitersAreSkipped() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        byte[] buffer = new byte[HEARTBEAT.length + 1];
        buffer[0] = 0x7E;
        System.arraycopy(HEARTBEAT, 0, buffer, 1, HEARTBEAT.length);
        channel.writeInbound(Unpooled.wrappedBuffer(buffer));
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        assertNull(channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("$ 私有帧按固定 32 字节切出，不等待标识位")
    void privateFrameIsSplitByFixedLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(PRIVATE_FRAME));
        assertArrayEquals(PRIVATE_FRAME, channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("缺少结束标识位的半帧保持缓存，补齐后一次产出")
    void incompleteFrameWaitsForMoreBytes() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOf(HEARTBEAT, 10)));
        assertNull(channel.readInbound());
        byte[] rest = Arrays.copyOfRange(HEARTBEAT, 10, HEARTBEAT.length);
        channel.writeInbound(Unpooled.wrappedBuffer(rest));
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        channel.finish();
    }

    @Test
    @DisplayName("没有标识位的超长噪声被丢弃，后续帧仍可正常切出")
    void oversizedGarbageIsDiscarded() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808FrameDecoder());
        byte[] garbage = new byte[1024 * 1024 + 16];
        Arrays.fill(garbage, (byte) 0x11);
        channel.writeInbound(Unpooled.wrappedBuffer(garbage));
        assertNull(channel.readInbound());
        channel.writeInbound(Unpooled.wrappedBuffer(HEARTBEAT));
        assertArrayEquals(HEARTBEAT, channel.readInbound());
        channel.finish();
    }

    private static byte[] hex(String hexText) {
        byte[] bytes = new byte[hexText.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(hexText.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }
}
