package com.cm.protocol.jt809;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * JT809 字节读写：全部多字节字段大端序，定长字符串按 GBK 写入并以 NUL 补齐。
 * 旧实现逐字节 toHexString 不补前导 0，遇到小于 0x10 的字节会把后续字段错位，这里不再复刻。
 */
public final class Jt809Bytes {

    private static final Charset GBK = Charset.forName("GBK");

    private static final int BYTE_MASK = 0xFF;

    private static final int SHORT_BYTES = 2;

    private static final int INT_BYTES = 4;

    private static final byte STRING_PAD = 0;

    private Jt809Bytes() {
    }

    public static int unsignedByte(byte[] data, int offset) {
        checkRange(data, offset, 1);
        return data[offset] & BYTE_MASK;
    }

    public static int unsignedShort(byte[] data, int offset) {
        checkRange(data, offset, SHORT_BYTES);
        return ((data[offset] & BYTE_MASK) << Byte.SIZE) | (data[offset + 1] & BYTE_MASK);
    }

    public static int signedInt(byte[] data, int offset) {
        checkRange(data, offset, INT_BYTES);
        return ((data[offset] & BYTE_MASK) << 24)
                | ((data[offset + 1] & BYTE_MASK) << 16)
                | ((data[offset + 2] & BYTE_MASK) << Byte.SIZE)
                | (data[offset + 3] & BYTE_MASK);
    }

    public static long unsignedInt(byte[] data, int offset) {
        return signedInt(data, offset) & 0xFFFFFFFFL;
    }

    public static byte[] readFixedBytes(byte[] data, int offset, int length) {
        checkRange(data, offset, length);
        byte[] value = new byte[length];
        System.arraycopy(data, offset, value, 0, length);
        return value;
    }

    /** 定长字符串：读到首个 NUL 为止，与旧 MessageParser.getString 口径一致。 */
    public static String readFixedString(byte[] data, int offset, int length) {
        checkRange(data, offset, length);
        int end = offset;
        while (end < offset + length && data[end] != STRING_PAD) {
            end++;
        }
        return new String(data, offset, end - offset, GBK);
    }

    public static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & BYTE_MASK);
    }

    public static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value >>> Byte.SIZE & BYTE_MASK);
        out.write(value & BYTE_MASK);
    }

    public static void writeInt(ByteArrayOutputStream out, long value) {
        out.write((int) (value >>> 24) & BYTE_MASK);
        out.write((int) (value >>> 16) & BYTE_MASK);
        out.write((int) (value >>> Byte.SIZE) & BYTE_MASK);
        out.write((int) value & BYTE_MASK);
    }

    public static void writeFixedString(ByteArrayOutputStream out, String value, int length) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(GBK);
        int count = Math.min(bytes.length, length);
        out.write(bytes, 0, count);
        for (int index = count; index < length; index++) {
            out.write(STRING_PAD);
        }
    }

    public static void checkRange(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IllegalArgumentException("message body is shorter than declared");
        }
    }
}

