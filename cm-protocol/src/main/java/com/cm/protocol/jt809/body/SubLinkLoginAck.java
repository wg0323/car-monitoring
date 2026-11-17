package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;

import java.io.ByteArrayOutputStream;

/**
 * 0x9002 从链路连接应答：只带 1 字节结果码。
 * 旧 DownConnectRsp.wrapper 恒发 0，日志帧的 27 字节长度即由此而来。
 */
public record SubLinkLoginAck(int result) {

    public static final int RESULT_SUCCESS = 0;

    public static final int BODY_LENGTH = 1;

    public static SubLinkLoginAck parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        return new SubLinkLoginAck(Jt809Bytes.unsignedByte(body, 0));
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        Jt809Bytes.writeByte(out, result);
        return out.toByteArray();
    }
}

