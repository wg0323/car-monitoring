package com.cm.protocol;

import java.io.ByteArrayOutputStream;

import com.cm.protocol.body.RegistrationResponse;

/**
 * JT808 下行报文编码。已覆盖平台通用应答与终端注册应答，其他下行消息体按需补充。
 */
public final class Jt808Encoder {

    private static final int PLATFORM_ACK_MESSAGE_ID = 0x8001;

    private static final int ACK_RESULT_SUCCESS = 0;

    private Jt808Encoder() {
    }

    /**
     * 编码 0x8001 平台通用应答，用于注册、鉴权、心跳与位置上报的受理确认。
     */
    public static byte[] genericAck(String simNo, int serialNo, int ackedSerialNo, int ackedMessageId,
            int result) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ProtocolBytes.writeShort(body, ackedSerialNo);
        ProtocolBytes.writeShort(body, ackedMessageId);
        ProtocolBytes.writeByte(body, result);
        return encode(MessageHeader.forDownlink(PLATFORM_ACK_MESSAGE_ID, simNo, serialNo, body.size()),
                body.toByteArray());
    }

    public static byte[] successAck(String simNo, int serialNo, int ackedSerialNo, int ackedMessageId) {
        return genericAck(simNo, serialNo, ackedSerialNo, ackedMessageId, ACK_RESULT_SUCCESS);
    }

    /**
     * 编码 0x8100 终端注册应答。结果 0 成功时带鉴权码，其它结果只写流水号与结果码。
     */
    public static byte[] registrationAck(String simNo, int serialNo, int registerSerialNo,
            int result, String authCode) {
        byte[] body = RegistrationResponse.of(registerSerialNo, result, authCode).toBytes();
        return encode(MessageHeader.forDownlink(RegistrationResponse.MESSAGE_ID, simNo, serialNo,
                body.length), body);
    }

    /**
     * 组装完整帧：消息头 + 消息体 + 异或校验码，转义后加首尾标识位。
     */
    public static byte[] encode(MessageHeader header, byte[] body) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        byte[] headerBytes = header.writeToBytes();
        payload.write(headerBytes, 0, headerBytes.length);
        if (body != null && body.length > 0) {
            payload.write(body, 0, body.length);
        }
        byte[] content = payload.toByteArray();
        byte checksum = FrameChecksum.xor(content, 0, content.length);
        ByteArrayOutputStream withChecksum = new ByteArrayOutputStream(content.length + 1);
        withChecksum.write(content, 0, content.length);
        withChecksum.write(checksum);
        return FrameEscaper.wrap(FrameEscaper.escape(withChecksum.toByteArray()));
    }
}
