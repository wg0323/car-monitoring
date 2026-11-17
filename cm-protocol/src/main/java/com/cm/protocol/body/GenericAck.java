package com.cm.protocol.body;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x0001 终端通用应答。应答消息 ID 按无符号 16 位读取，0x8103 会被渲染成 33027。
 */
public final class GenericAck implements MessageBody {

    public static final int MESSAGE_ID = 0x0001;

    private static final int BODY_LENGTH = 5;

    private final int responseSerialNo;

    private final int responseMessageId;

    private final int result;

    private GenericAck(int responseSerialNo, int responseMessageId, int result) {
        this.responseSerialNo = responseSerialNo;
        this.responseMessageId = responseMessageId;
        this.result = result;
    }

    public static GenericAck parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        if (length < BODY_LENGTH) {
            throw new IllegalArgumentException("generic ack body is shorter than " + BODY_LENGTH);
        }
        return new GenericAck(ProtocolBytes.unsignedShort(body, offset),
                ProtocolBytes.unsignedShort(body, offset + 2), ProtocolBytes.unsignedByte(body, offset + 4));
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public int responseSerialNo() {
        return responseSerialNo;
    }

    public int responseMessageId() {
        return responseMessageId;
    }

    public int result() {
        return result;
    }
}
