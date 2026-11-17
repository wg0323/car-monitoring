package com.cm.protocol.jt809;

import java.io.ByteArrayOutputStream;

/**
 * 车牌号字段：21 字节 GBK 定长，不足补 NUL，超长截断。
 * 旧实现按字符拼 hex，遇到单字节小于 0x10 会把后续字段错位；新实现直接写字节。
 */
public final class Jt809Plate {

    private Jt809Plate() {
    }

    public static byte[] write(String plateNo) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Jt809Constants.PLATE_LENGTH);
        Jt809Bytes.writeFixedString(out, plateNo, Jt809Constants.PLATE_LENGTH);
        return out.toByteArray();
    }

    public static String read(byte[] body, int offset) {
        return Jt809Bytes.readFixedString(body, offset, Jt809Constants.PLATE_LENGTH);
    }
}

