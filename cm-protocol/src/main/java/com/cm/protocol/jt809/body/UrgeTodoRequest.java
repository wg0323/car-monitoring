package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Plate;

import java.io.ByteArrayOutputStream;

/**
 * 0x1400/0x1400 报警督办请求。
 * 字段顺序取自旧 DownWarnMsgTodoReq.handle：车牌(21) + 颜色(1) + 子类型(2) + 后续长度(4)
 * + 报警源(1) + 报警类型(2) + 报警时间(8) + 督办 ID(4) + 督办截止(8) + 级别(1)
 * + 督办人(16) + 电话(20) + 邮箱(30)，后续长度 90。时间为 UTC 秒。
 */
public record UrgeTodoRequest(String plateNo, int plateColor, int warnSource, int warnType,
                              long warnTimeEpochSecond, long superviseId, long superviseEndEpochSecond,
                              int supervisionLevel, String supervisor, String supervisorTel,
                              String supervisorEmail) {

    private static final int COLOR_LENGTH = 1;

    private static final int SUB_TYPE_LENGTH = 2;

    private static final int DATA_LENGTH_FIELD = 4;

    private static final int WARN_SOURCE_LENGTH = 1;

    private static final int WARN_TYPE_LENGTH = 2;

    private static final int EPOCH_LENGTH = 8;

    private static final int SUPERVISE_ID_LENGTH = 4;

    private static final int LEVEL_LENGTH = 1;

    private static final int SUPERVISOR_LENGTH = 16;

    private static final int TEL_LENGTH = 20;

    private static final int EMAIL_LENGTH = 30;

    private static final int PREFIX_LENGTH = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH
            + SUB_TYPE_LENGTH + DATA_LENGTH_FIELD;

    public static final int BODY_LENGTH = PREFIX_LENGTH + Jt809Constants.URGE_TODO_REQUEST_DATA_LENGTH;

    public static UrgeTodoRequest parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        String plateNo = Jt809Plate.read(body, 0);
        int plateColor = Jt809Bytes.unsignedByte(body, Jt809Constants.PLATE_LENGTH);
        int subTypeOffset = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH;
        int subType = Jt809Bytes.unsignedShort(body, subTypeOffset);
        if (subType != Jt809Constants.SUB_TYPE_URGE_TODO_REQUEST) {
            throw new IllegalArgumentException("unexpected urge todo sub type: " + subType);
        }
        int dataLength = Jt809Bytes.signedInt(body, subTypeOffset + SUB_TYPE_LENGTH);
        if (dataLength != Jt809Constants.URGE_TODO_REQUEST_DATA_LENGTH) {
            throw new IllegalArgumentException("unexpected urge todo data length: " + dataLength);
        }
        int index = PREFIX_LENGTH;
        int warnSource = Jt809Bytes.unsignedByte(body, index);
        index += WARN_SOURCE_LENGTH;
        int warnType = Jt809Bytes.unsignedShort(body, index);
        index += WARN_TYPE_LENGTH;
        long warnTime = Jt809Bytes.unsignedInt(body, index) << 32 | Jt809Bytes.unsignedInt(body, index + 4);
        index += EPOCH_LENGTH;
        long superviseId = Jt809Bytes.unsignedInt(body, index);
        index += SUPERVISE_ID_LENGTH;
        long superviseEnd = Jt809Bytes.unsignedInt(body, index) << 32
                | Jt809Bytes.unsignedInt(body, index + 4);
        index += EPOCH_LENGTH;
        int supervisionLevel = Jt809Bytes.unsignedByte(body, index);
        index += LEVEL_LENGTH;
        String supervisor = Jt809Bytes.readFixedString(body, index, SUPERVISOR_LENGTH);
        index += SUPERVISOR_LENGTH;
        String supervisorTel = Jt809Bytes.readFixedString(body, index, TEL_LENGTH);
        index += TEL_LENGTH;
        String supervisorEmail = Jt809Bytes.readFixedString(body, index, EMAIL_LENGTH);
        return new UrgeTodoRequest(plateNo, plateColor, warnSource, warnType, warnTime, superviseId,
                superviseEnd, supervisionLevel, supervisor, supervisorTel, supervisorEmail);
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        out.writeBytes(Jt809Plate.write(plateNo));
        Jt809Bytes.writeByte(out, plateColor);
        Jt809Bytes.writeShort(out, Jt809Constants.SUB_TYPE_URGE_TODO_REQUEST);
        Jt809Bytes.writeInt(out, Jt809Constants.URGE_TODO_REQUEST_DATA_LENGTH);
        Jt809Bytes.writeByte(out, warnSource);
        Jt809Bytes.writeShort(out, warnType);
        writeEpochSecond(out, warnTimeEpochSecond);
        Jt809Bytes.writeInt(out, superviseId);
        writeEpochSecond(out, superviseEndEpochSecond);
        Jt809Bytes.writeByte(out, supervisionLevel);
        Jt809Bytes.writeFixedString(out, supervisor, SUPERVISOR_LENGTH);
        Jt809Bytes.writeFixedString(out, supervisorTel, TEL_LENGTH);
        Jt809Bytes.writeFixedString(out, supervisorEmail, EMAIL_LENGTH);
        return out.toByteArray();
    }

    private static void writeEpochSecond(ByteArrayOutputStream out, long epochSecond) {
        Jt809Bytes.writeInt(out, epochSecond >>> 32);
        Jt809Bytes.writeInt(out, epochSecond & 0xFFFFFFFFL);
    }
}

