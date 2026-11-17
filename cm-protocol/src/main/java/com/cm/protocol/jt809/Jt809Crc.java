package com.cm.protocol.jt809;

/**
 * CRC-16/CCITT-FALSE：初值 0xFFFF、多项式 0x1021、不反转、无终异或，
 * 与旧实现 Tools.getCRCString 逐位算法一致，计算范围是 5B 与 5D 之间的全部字节。
 */
public final class Jt809Crc {

    private static final int INITIAL = 0xFFFF;

    private static final int POLYNOMIAL = 0x1021;

    private static final int BITS_PER_BYTE = 8;

    private static final int HIGH_BIT = 15;

    private static final int BYTE_MASK = 0xFF;

    private Jt809Crc() {
    }

    public static int compute(byte[] data, int offset, int length) {
        Jt809Bytes.checkRange(data, offset, length);
        int crc = INITIAL;
        for (int index = offset; index < offset + length; index++) {
            crc = update(crc, data[index] & BYTE_MASK);
        }
        return crc;
    }

    private static int update(int crc, int value) {
        int current = crc;
        for (int bit = 0; bit < BITS_PER_BYTE; bit++) {
            boolean incoming = (value >>> BITS_PER_BYTE - 1 - bit & 1) == 1;
            boolean high = (current >>> HIGH_BIT & 1) == 1;
            current = current << 1 & INITIAL;
            if (high != incoming) {
                current ^= POLYNOMIAL;
            }
        }
        return current;
    }
}

