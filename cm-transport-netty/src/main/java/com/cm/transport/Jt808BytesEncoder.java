package com.cm.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 下行字节编码。业务侧只产出完整的 JT808 帧字节（含标识位、转义与校验码），
 * 这里只把 byte[] 包成 ByteBuf。
 *
 * <p>没有这一层，NioSocketChannel 会以 unsupported message type 拒绝写入，
 * 平台应答在真实连接上根本发不出去（EmbeddedChannel 不会暴露这个问题）。
 */
public final class Jt808BytesEncoder extends MessageToByteEncoder<byte[]> {

    @Override
    protected void encode(ChannelHandlerContext context, byte[] message, ByteBuf out) {
        out.writeBytes(message);
    }
}
