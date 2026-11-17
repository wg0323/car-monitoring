package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Plate;

import java.io.ByteArrayOutputStream;

/**
 * 0x1200/0x1202 实时定位：车牌(21) + 颜色(1) + 子类型(2) + 后续数据长度(4) + 36 字节定位。
 * 字段顺序取自旧 UpExgMsgRealLocation.wrapper，后续数据长度固定 36。
 */
public record VehicleLocation(String plateNo, int plateColor, LocationSample sample) {

    private static final int COLOR_LENGTH = 1;

    private static final int SUB_TYPE_LENGTH = 2;

    private static final int DATA_LENGTH_FIELD = 4;

    private static final int PREFIX_LENGTH = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH
            + SUB_TYPE_LENGTH + DATA_LENGTH_FIELD;

    public static final int BODY_LENGTH = PREFIX_LENGTH + Jt809Constants.LOCATION_SAMPLE_LENGTH;

    public static VehicleLocation parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, BODY_LENGTH);
        String plateNo = Jt809Plate.read(body, 0);
        int plateColor = Jt809Bytes.unsignedByte(body, Jt809Constants.PLATE_LENGTH);
        int subTypeOffset = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH;
        int subType = Jt809Bytes.unsignedShort(body, subTypeOffset);
        if (subType != Jt809Constants.SUB_TYPE_REAL_LOCATION) {
            throw new IllegalArgumentException("unexpected location sub type: " + subType);
        }
        int dataLength = Jt809Bytes.signedInt(body, subTypeOffset + SUB_TYPE_LENGTH);
        if (dataLength != Jt809Constants.LOCATION_SAMPLE_LENGTH) {
            throw new IllegalArgumentException("unexpected real location data length: " + dataLength);
        }
        return new VehicleLocation(plateNo, plateColor, LocationSample.parse(body, PREFIX_LENGTH));
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(BODY_LENGTH);
        out.writeBytes(Jt809Plate.write(plateNo));
        Jt809Bytes.writeByte(out, plateColor);
        Jt809Bytes.writeShort(out, Jt809Constants.SUB_TYPE_REAL_LOCATION);
        Jt809Bytes.writeInt(out, Jt809Constants.LOCATION_SAMPLE_LENGTH);
        sample.write(out);
        return out.toByteArray();
    }
}

