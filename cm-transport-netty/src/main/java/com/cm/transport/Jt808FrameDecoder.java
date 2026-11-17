package com.cm.transport;

import java.util.List;

import com.cm.protocol.FrameConstants;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * JT808 字节流切帧。只负责识别帧边界并把完整帧交给下游，不做解码与业务判断：
 * 标准帧以 0x7E 界定，$ 私有定位帧为固定 32 字节且不带标识位。
 */
public final class Jt808FrameDecoder extends ByteToMessageDecoder {

    private static final byte PRIVATE_PREFIX = 0x24;

    private static final int PRIVATE_FRAME_LENGTH = 32;

    private static final int DELIMITER_COUNT = 1;

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            int readerIndex = in.readerIndex();
            if (in.getByte(readerIndex) == PRIVATE_PREFIX) {
                if (in.readableBytes() < PRIVATE_FRAME_LENGTH) {
                    return;
                }
                out.add(readBytes(in, PRIVATE_FRAME_LENGTH));
                continue;
            }
            int start = in.indexOf(readerIndex, in.writerIndex(), FrameConstants.FRAME_DELIMITER);
            if (start < 0) {
                discardOverflow(in);
                return;
            }
            if (start > readerIndex) {
                in.skipBytes(start - readerIndex);
            }
            int bodyStart = start + DELIMITER_COUNT;
            int end = in.indexOf(bodyStart, in.writerIndex(), FrameConstants.FRAME_DELIMITER);
            if (end < 0) {
                discardOverflow(in);
                return;
            }
            if (end == bodyStart) {
                in.skipBytes(DELIMITER_COUNT);
                continue;
            }
            int frameLength = end - start + DELIMITER_COUNT;
            if (frameLength > FrameConstants.MAX_FRAME_LENGTH) {
                in.skipBytes(frameLength);
                continue;
            }
            out.add(readBytes(in, frameLength));
        }
    }

    private static byte[] readBytes(ByteBuf in, int length) {
        byte[] frame = new byte[length];
        in.readBytes(frame);
        return frame;
    }

    /**
     * 无分隔符的超长缓存视为噪声流，直接丢弃，避免连接被无边界的脏数据拖住内存。
     */
    private static void discardOverflow(ByteBuf in) {
        if (in.readableBytes() > FrameConstants.MAX_FRAME_LENGTH) {
            in.skipBytes(in.readableBytes());
        }
    }
}
