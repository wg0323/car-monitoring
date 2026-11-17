package com.cm.protocol.body;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x0201 位置信息查询应答。点名 0x8201 的终端应答走这条链路，属于「业务应答即成功」。
 */
public final class PositionQueryAck implements MessageBody {

    public static final int MESSAGE_ID = 0x0201;

    private static final int SERIAL_LENGTH = 2;

    private final int responseSerialNo;

    private final PositionReport position;

    private PositionQueryAck(int responseSerialNo, PositionReport position) {
        this.responseSerialNo = responseSerialNo;
        this.position = position;
    }

    public static PositionQueryAck parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        if (length <= SERIAL_LENGTH) {
            throw new IllegalArgumentException("position query ack has no position body");
        }
        int responseSerialNo = ProtocolBytes.unsignedShort(body, offset);
        PositionReport position = PositionReport.parse(body, offset + SERIAL_LENGTH,
                length - SERIAL_LENGTH);
        return new PositionQueryAck(responseSerialNo, position);
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public int responseSerialNo() {
        return responseSerialNo;
    }

    public PositionReport position() {
        return position;
    }
}
