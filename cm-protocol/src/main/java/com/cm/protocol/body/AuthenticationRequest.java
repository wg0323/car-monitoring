package com.cm.protocol.body;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x0102 终端鉴权。鉴权码长度由消息体长度决定，按 GBK 解码。
 */
public final class AuthenticationRequest implements MessageBody {

    public static final int MESSAGE_ID = 0x0102;

    private final String registerNo;

    private AuthenticationRequest(String registerNo) {
        this.registerNo = registerNo;
    }

    public static AuthenticationRequest parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        return new AuthenticationRequest(ProtocolBytes.gbkString(body, offset, length));
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public String registerNo() {
        return registerNo;
    }
}
