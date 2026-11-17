package com.cm.transport;

import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Frame;
import com.cm.protocol.jt809.Jt809FrameCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * JT809 字节流切帧。帧内内容已转义，所以流里出现的 0x5D 只可能是帧尾；
 * 切出 5B..5D 后交给协议层校验长度与 CRC，非法帧只丢一字节重新同步，不断开连接。
 */
public final class Jt809FrameDecoder extends ByteToMessageDecoder {

    private static final int BYTE_MASK = 0xFF;

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            int readerIndex = in.readerIndex();
            if ((in.getByte(readerIndex) & BYTE_MASK) != Jt809Constants.HEAD_FLAG) {
                in.skipBytes(1);
                continue;
            }
            int end = in.indexOf(readerIndex + 1, in.writerIndex(), (byte) Jt809Constants.TAIL_FLAG);
            if (end < 0) {
                discardOverflow(in);
                return;
            }
            int frameLength = end - readerIndex + 1;
            byte[] frame = new byte[frameLength];
            in.getBytes(readerIndex, frame);
            try {
                Jt809Frame decoded = Jt809FrameCodec.decode(frame);
                in.skipBytes(frameLength);
                out.add(decoded);
            } catch (IllegalArgumentException ex) {
                // 先跳 1 字节再找下一个帧头，避免一个坏包把整段缓存停在原地。
                in.skipBytes(1);
            }
        }
    }

    /** 无帧尾的超长缓存视为噪声流，直接丢弃，避免连接被脏数据拖住内存。 */
    private static void discardOverflow(ByteBuf in) {
        if (in.readableBytes() > Jt809Constants.MAX_FRAME_LENGTH) {
            in.skipBytes(in.readableBytes());
        }
    }
}
 
