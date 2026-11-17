package com.cm.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 下行字节编码：完整帧字节应原样写入 ByteBuf，不重新转义也不重算校验。
 */
class Jt808BytesEncoderTest {

    private static final byte[] FRAME = {0x7e, 0x01, 0x02, 0x7d, 0x02, 0x7e};

    @Test
    @DisplayName("byte[] 下行消息原样写入 ByteBuf")
    void should_wrap_bytes_into_byte_buf() {
        EmbeddedChannel channel = new EmbeddedChannel(new Jt808BytesEncoder());

        channel.writeOutbound(FRAME);

        ByteBuf outbound = channel.readOutbound();
        assertNotNull(outbound, "应产出 ByteBuf");
        byte[] written = new byte[outbound.readableBytes()];
        outbound.readBytes(written);
        outbound.release();
        assertEquals(FRAME.length, written.length);
        for (int index = 0; index < FRAME.length; index++) {
            assertEquals(FRAME[index], written[index]);
        }
        channel.finishAndReleaseAll();
    }
}
