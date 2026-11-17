package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;

import java.io.ByteArrayOutputStream;

/**
 * 0x1001 主链路登录请求：账号(4) + 密码(8) + 从链路 IP(32) + 从链路端口(2)。
 * 字段顺序取自旧实现 UpConnectReq.wrapper，密码与 IP 为 GBK 定长字符串。
 */
public record MainLinkLogin(long userId, String password, String subLinkIp, int subLinkPort) {

    private static final int USER_ID_LENGTH = 4;

    private static final int PASSWORD_LENGTH = 8;

    private static final int IP_LENGTH = 32;

    private static final int PORT_LENGTH = 2;

    public static final int BODY_LENGTH = USER_ID_LENGTH + PASSWORD_LENGTH + IP_LENGTH + PORT_LENGTH;

    public static MainLinkLogin parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        long userId = Jt809Bytes.unsignedInt(body, 0);
        String password = Jt809Bytes.readFixedString(body, USER_ID_LENGTH, PASSWORD_LENGTH);
        String ip = Jt809Bytes.readFixedString(body, USER_ID_LENGTH + PASSWORD_LENGTH, IP_LENGTH);
        int port = Jt809Bytes.unsignedShort(body, USER_ID_LENGTH + PASSWORD_LENGTH + IP_LENGTH);
        return new MainLinkLogin(userId, password, ip, port);
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        Jt809Bytes.writeInt(out, userId);
        Jt809Bytes.writeFixedString(out, password, PASSWORD_LENGTH);
        Jt809Bytes.writeFixedString(out, subLinkIp, IP_LENGTH);
        Jt809Bytes.writeShort(out, subLinkPort);
        return out.toByteArray();
    }
}

