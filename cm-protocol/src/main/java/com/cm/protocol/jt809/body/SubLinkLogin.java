package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;

import java.io.ByteArrayOutputStream;

/**
 * 0x9001 从链路连接请求：校验码(4)，即下级平台的接入码。
 * 旧 DownConnectReq.wrapper 只写这 4 字节，日志里的“校验码:8992”就是它。
 */
public record SubLinkLogin(long verifyCode) {

    private static final int VERIFY_CODE_LENGTH = 4;

    public static final int BODY_LENGTH = VERIFY_CODE_LENGTH;

    public static SubLinkLogin parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        return new SubLinkLogin(Jt809Bytes.unsignedInt(body, 0));
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        Jt809Bytes.writeInt(out, verifyCode);
        return out.toByteArray();
    }
}

