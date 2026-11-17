package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Plate;

import java.io.ByteArrayOutputStream;

/**
 * 0x1400/0x1401 报警督办应答：车牌(21) + 颜色(1) + 子类型(2) + 后续长度(4)
 * + 督办 ID(4) + 应答结果(1)。
 * 字段顺序取自旧 UpWarnMsgUrgeToDoAck.wrapper，后续长度固定 5。
 */
public record UrgeTodoAck(String plateNo, int plateColor, long superviseId, int result) {

    private static final int COLOR_LENGTH = 1;

    private static final int SUB_TYPE_LENGTH = 2;

    private static final int DATA_LENGTH_FIELD = 4;

    private static final int SUPERVISE_ID_LENGTH = 4;

    private static final int RESULT_LENGTH = 1;

    private static final int PREFIX_LENGTH = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH
            + SUB_TYPE_LENGTH + DATA_LENGTH_FIELD;

    public static final int BODY_LENGTH = PREFIX_LENGTH + Jt809Constants.URGE_TODO_ACK_DATA_LENGTH;

    public static UrgeTodoAck parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        String plateNo = Jt809Plate.read(body, 0);
        int plateColor = Jt809Bytes.unsignedByte(body, Jt809Constants.PLATE_LENGTH);
        int subTypeOffset = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH;
        int subType = Jt809Bytes.unsignedShort(body, subTypeOffset);
        if (subType != Jt809Constants.SUB_TYPE_URGE_TODO_ACK) {
            throw new IllegalArgumentException("unexpected urge todo ack sub type: " + subType);
        }
        int dataLength = Jt809Bytes.signedInt(body, subTypeOffset + SUB_TYPE_LENGTH);
        if (dataLength != Jt809Constants.URGE_TODO_ACK_DATA_LENGTH) {
            throw new IllegalArgumentException("unexpected urge todo ack data length: " + dataLength);
        }
        long superviseId = Jt809Bytes.unsignedInt(body, PREFIX_LENGTH);
        int result = Jt809Bytes.unsignedByte(body, PREFIX_LENGTH + SUPERVISE_ID_LENGTH);
        return new UrgeTodoAck(plateNo, plateColor, superviseId, result);
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        out.writeBytes(Jt809Plate.write(plateNo));
        Jt809Bytes.writeByte(out, plateColor);
        Jt809Bytes.writeShort(out, Jt809Constants.SUB_TYPE_URGE_TODO_ACK);
        Jt809Bytes.writeInt(out, Jt809Constants.URGE_TODO_ACK_DATA_LENGTH);
        Jt809Bytes.writeInt(out, superviseId);
        Jt809Bytes.writeByte(out, result);
        return out.toByteArray();
    }
}

