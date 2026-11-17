package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;

import java.io.ByteArrayOutputStream;

/**
 * 0x1002 主链路登录应答：应答结果(1) + 校验码(4)。
 * 结果码口径与旧 UpConnectRsp.handle 一致：0 成功、1 IP 错、2 接入码错、3 未注册、4 密码错、5 资源紧张、6 其他。
 */
public record MainLinkLoginAck(int result, long verifyCode) {

    public static final int RESULT_SUCCESS = 0;

    public static final int RESULT_IP_INVALID = 1;

    public static final int RESULT_CENTER_INVALID = 2;

    public static final int RESULT_USER_NOT_REGISTERED = 3;

    public static final int RESULT_PASSWORD_INVALID = 4;

    public static final int RESULT_RESOURCE_BUSY = 5;

    public static final int RESULT_OTHER = 6;

    private static final int RESULT_LENGTH = 1;

    private static final int VERIFY_CODE_LENGTH = 4;

    public static final int BODY_LENGTH = RESULT_LENGTH + VERIFY_CODE_LENGTH;

    public static MainLinkLoginAck parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        int result = Jt809Bytes.unsignedByte(body, 0);
        long verifyCode = Jt809Bytes.unsignedInt(body, RESULT_LENGTH);
        return new MainLinkLoginAck(result, verifyCode);
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        Jt809Bytes.writeByte(out, result);
        Jt809Bytes.writeInt(out, verifyCode);
        return out.toByteArray();
    }
}

