package com.cm.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;

/**
 * 协议字节读写工具。JT808 全部多字节字段采用大端序，BCD 时间字段按半字节十进制解释。
 */
public final class ProtocolBytes {

    private static final Charset GBK = Charset.forName("GBK");

    private static final int BYTE_MASK = 0xFF;

    private static final long INT_MASK = 0xFFFFFFFFL;

    private static final int BCD_RADIX = 10;

    private static final int TIME_COMPONENT_COUNT = 6;

    private ProtocolBytes() {
    }

    public static int unsignedByte(byte[] data, int offset) {
        checkRange(data, offset, 1);
        return data[offset] & BYTE_MASK;
    }

    public static int unsignedShort(byte[] data, int offset) {
        checkRange(data, offset, 2);
        return ((data[offset] & BYTE_MASK) << Byte.SIZE) | (data[offset + 1] & BYTE_MASK);
    }

    public static int signedInt(byte[] data, int offset) {
        checkRange(data, offset, Integer.BYTES);
        return ((data[offset] & BYTE_MASK) << 24)
                | ((data[offset + 1] & BYTE_MASK) << 16)
                | ((data[offset + 2] & BYTE_MASK) << Byte.SIZE)
                | (data[offset + 3] & BYTE_MASK);
    }

    public static long unsignedInt(byte[] data, int offset) {
        return signedInt(data, offset) & INT_MASK;
    }

    /**
     * 把 6 字节 BCD 时间解析为设备本地时间。旧实现按十六进制字符渲染，合法 BCD 数据下结果一致。
     */
    public static LocalDateTime bcdDateTime(byte[] data, int offset) {
        checkRange(data, offset, TIME_COMPONENT_COUNT);
        int year = 2000 + bcdByte(data[offset]);
        int month = bcdByte(data[offset + 1]);
        int day = bcdByte(data[offset + 2]);
        int hour = bcdByte(data[offset + 3]);
        int minute = bcdByte(data[offset + 4]);
        int second = bcdByte(data[offset + 5]);
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    /**
     * 按 BCD 规则把每个字节渲染成两位十进制字符，不去除前导 0。
     */
    public static String bcdString(byte[] data, int offset, int length) {
        checkRange(data, offset, length);
        StringBuilder text = new StringBuilder(length * 2);
        for (int index = offset; index < offset + length; index++) {
            text.append(data[index] >> 4 & 0x0F).append(data[index] & 0x0F);
        }
        return text.toString();
    }

    /**
     * 按 GBK 解码定长字段，遇到 NUL 截断，与旧编码器写入的定长字符串口径一致。
     */
    public static String gbkString(byte[] data, int offset, int length) {
        checkRange(data, offset, length);
        int end = offset;
        while (end < offset + length && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, GBK);
    }

    public static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & BYTE_MASK);
    }

    public static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value >> Byte.SIZE & BYTE_MASK);
        out.write(value & BYTE_MASK);
    }

    public static void checkRange(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IllegalArgumentException("message body is shorter than declared");
        }
    }

    static int bcdByte(byte value) {
        int high = value >> 4 & 0x0F;
        int low = value & 0x0F;
        if (high > BCD_RADIX - 1 || low > BCD_RADIX - 1) {
            throw new IllegalArgumentException("invalid BCD digit");
        }
        return high * BCD_RADIX + low;
    }
}
