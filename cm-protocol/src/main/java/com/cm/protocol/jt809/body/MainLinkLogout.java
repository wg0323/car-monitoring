package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;

import java.io.ByteArrayOutputStream;

/**
 * 0x1003 主链路注销请求：账号(4) + 密码(8)，与旧 UpDisconnectReq.wrapper 一致。
 * 对应的 0x1004 / 0x9004 应答没有消息体，直接空 body 编帧。
 */
public record MainLinkLogout(long userId, String password) {

    private static final int USER_ID_LENGTH = 4;

    private static final int PASSWORD_LENGTH = 8;

    public static final int BODY_LENGTH = USER_ID_LENGTH + PASSWORD_LENGTH;

    public static MainLinkLogout parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        long userId = Jt809Bytes.unsignedInt(body, 0);
        String password = Jt809Bytes.readFixedString(body, USER_ID_LENGTH, PASSWORD_LENGTH);
        return new MainLinkLogout(userId, password);
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        Jt809Bytes.writeInt(out, userId);
        Jt809Bytes.writeFixedString(out, password, PASSWORD_LENGTH);
        return out.toByteArray();
    }
}

