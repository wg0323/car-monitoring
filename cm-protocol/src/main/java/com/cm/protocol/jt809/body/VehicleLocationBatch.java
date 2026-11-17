package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Plate;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 0x1200/0x1203 补发定位：车牌(21) + 颜色(1) + 子类型(2) + 后续数据长度(4) + 条数(1) + 36×n。
 * 后续数据长度 = 1 + 36n，即含条数字段本身；旧 UpExgMsgHistoryLocation 同口径。
 */
public record VehicleLocationBatch(String plateNo, int plateColor, List<LocationSample> samples) {

    private static final int COLOR_LENGTH = 1;

    private static final int SUB_TYPE_LENGTH = 2;

    private static final int DATA_LENGTH_FIELD = 4;

    private static final int COUNT_LENGTH = 1;

    private static final int PREFIX_LENGTH = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH
            + SUB_TYPE_LENGTH + DATA_LENGTH_FIELD + COUNT_LENGTH;

    private static final int MAX_SAMPLES = 200;

    public static VehicleLocationBatch parse(byte[] body) {
        Jt809Bytes.checkRange(body, 0, PREFIX_LENGTH);
        String plateNo = Jt809Plate.read(body, 0);
        int plateColor = Jt809Bytes.unsignedByte(body, Jt809Constants.PLATE_LENGTH);
        int subTypeOffset = Jt809Constants.PLATE_LENGTH + COLOR_LENGTH;
        int subType = Jt809Bytes.unsignedShort(body, subTypeOffset);
        if (subType != Jt809Constants.SUB_TYPE_HISTORY_LOCATION) {
            throw new IllegalArgumentException("unexpected history location sub type: " + subType);
        }
        int dataLength = Jt809Bytes.signedInt(body, subTypeOffset + SUB_TYPE_LENGTH);
        int countOffset = subTypeOffset + SUB_TYPE_LENGTH + DATA_LENGTH_FIELD;
        int count = Jt809Bytes.unsignedByte(body, countOffset);
        if (dataLength != COUNT_LENGTH + Jt809Constants.LOCATION_SAMPLE_LENGTH * count) {
            throw new IllegalArgumentException("history location data length mismatch: " + dataLength);
        }
        Jt809Bytes.checkRange(body, PREFIX_LENGTH, Jt809Constants.LOCATION_SAMPLE_LENGTH * count);
        List<LocationSample> samples = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            samples.add(LocationSample.parse(body,
                    PREFIX_LENGTH + Jt809Constants.LOCATION_SAMPLE_LENGTH * index));
        }
        return new VehicleLocationBatch(plateNo, plateColor, samples);
    }

    public byte[] encode() {
        if (samples == null || samples.isEmpty() || samples.size() > MAX_SAMPLES) {
            throw new IllegalArgumentException("history location samples must be 1.." + MAX_SAMPLES);
        }
        int dataLength = COUNT_LENGTH + Jt809Constants.LOCATION_SAMPLE_LENGTH * samples.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream(PREFIX_LENGTH
                + Jt809Constants.LOCATION_SAMPLE_LENGTH * samples.size());
        out.writeBytes(Jt809Plate.write(plateNo));
        Jt809Bytes.writeByte(out, plateColor);
        Jt809Bytes.writeShort(out, Jt809Constants.SUB_TYPE_HISTORY_LOCATION);
        Jt809Bytes.writeInt(out, dataLength);
        Jt809Bytes.writeByte(out, samples.size());
        for (LocationSample sample : samples) {
            sample.write(out);
        }
        return out.toByteArray();
    }
}

