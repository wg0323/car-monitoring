package com.cm.protocol.jt809;

import java.io.ByteArrayOutputStream;

/**
 * 帧内转义：5A→5A02、5B→5A01、5E→5E02、5D→5E01。
 * 与旧实现 encoderStringEscape / decoderStringEscape 的映射一致，首尾标识位不参与转义。
 */
public final class Jt809Escape {

    private static final int BYTE_MASK = 0xFF;

    private static final int PREFIX_A = 0x5A;

    private static final int PREFIX_E = 0x5E;

    private static final int SUFFIX_01 = 0x01;

    private static final int SUFFIX_02 = 0x02;

    private static final int ESCAPED_5A = 0x5A;

    private static final int ESCAPED_5B = 0x5B;

    private static final int ESCAPED_5D = 0x5D;

    private static final int ESCAPED_5E = 0x5E;

    private Jt809Escape() {
    }

    public static void escape(byte[] data, int offset, int length, ByteArrayOutputStream out) {
        Jt809Bytes.checkRange(data, offset, length);
        for (int index = offset; index < offset + length; index++) {
            int value = data[index] & BYTE_MASK;
            switch (value) {
                case ESCAPED_5A -> writePair(out, PREFIX_A, SUFFIX_02);
                case ESCAPED_5B -> writePair(out, PREFIX_A, SUFFIX_01);
                case ESCAPED_5E -> writePair(out, PREFIX_E, SUFFIX_02);
                case ESCAPED_5D -> writePair(out, PREFIX_E, SUFFIX_01);
                default -> out.write(value);
            }
        }
    }

    public static byte[] unescape(byte[] data, int offset, int length) {
        Jt809Bytes.checkRange(data, offset, length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        int index = offset;
        int end = offset + length;
        while (index < end) {
            int value = data[index++] & BYTE_MASK;
            if (value == PREFIX_A || value == PREFIX_E) {
                if (index >= end) {
                    throw new IllegalArgumentException("escaped frame ends with prefix byte");
                }
                int suffix = data[index++] & BYTE_MASK;
                out.write(restore(value, suffix));
            } else {
                out.write(value);
            }
        }
        return out.toByteArray();
    }

    private static void writePair(ByteArrayOutputStream out, int prefix, int suffix) {
        out.write(prefix);
        out.write(suffix);
    }

    private static int restore(int prefix, int suffix) {
        if (prefix == PREFIX_A && suffix == SUFFIX_01) {
            return ESCAPED_5B;
        }
        if (prefix == PREFIX_A && suffix == SUFFIX_02) {
            return ESCAPED_5A;
        }
        if (prefix == PREFIX_E && suffix == SUFFIX_01) {
            return ESCAPED_5D;
        }
        if (prefix == PREFIX_E && suffix == SUFFIX_02) {
            return ESCAPED_5E;
        }
        throw new IllegalArgumentException("illegal escape pair: " + prefix + "/" + suffix);
    }
}

