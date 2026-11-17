package com.cm.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * JT809 下行字节编码。业务侧只产出完整帧字节（含转义与校验码），这里只包成 ByteBuf；
 * 没有这一层 NioSocketChannel 会以 unsupported message type 拒绝写入。
 */
public final class Jt809BytesEncoder extends MessageToByteEncoder<byte[]> {

    @Override
    protected void encode(ChannelHandlerContext context, byte[] message, ByteBuf out) {
        out.writeBytes(message);
    }
}
 
