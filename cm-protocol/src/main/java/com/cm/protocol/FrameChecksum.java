package com.cm.protocol;

/**
 * JT808 异或校验。校验范围覆盖帧头到消息体的最后一个字节，不含标识位与校验码自身。
 */
public final class FrameChecksum {

    private FrameChecksum() {
    }

    public static byte xor(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IllegalArgumentException("checksum range out of bounds");
        }
        byte result = 0;
        for (int i = offset; i < offset + length; i++) {
            result ^= data[i];
        }
        return result;
    }

    public static boolean matches(byte[] data, int offset, int length, byte expected) {
        return xor(data, offset, length) == expected;
    }
}
