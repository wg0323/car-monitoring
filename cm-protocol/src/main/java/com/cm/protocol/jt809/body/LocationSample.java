package com.cm.protocol.jt809.body;

import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

/**
 * 0x1202 / 0x1203 的单条定位：36 字节。
 * 时间字段是“数值”而不是 BCD：日1B、月1B、年2B（2026→0x07EA）、时1B、分1B、秒1B；
 * 坐标单位为 1e-6 度，速度与里程与旧 GnssData 同口径（公里/小时、公里）。
 */
public record LocationSample(int posEncrypt, LocalDateTime deviceTime, int longitude, int latitude,
                             int speed, int recorderSpeed, long totalMileage, int direction,
                             int altitude, long vehicleStatus, long alarmStatus) {

    private static final int POS_ENCRYPT_LENGTH = 1;

    private static final int DAY_LENGTH = 1;

    private static final int MONTH_LENGTH = 1;

    private static final int YEAR_LENGTH = 2;

    private static final int TIME_PART_LENGTH = 1;

    private static final int COORDINATE_LENGTH = 4;

    private static final int SPEED_LENGTH = 2;

    private static final int MILEAGE_LENGTH = 4;

    private static final int DIRECTION_LENGTH = 2;

    private static final int ALTITUDE_LENGTH = 2;

    private static final int STATE_LENGTH = 4;

    public static final int BODY_LENGTH = Jt809Constants.LOCATION_SAMPLE_LENGTH;

    public static LocationSample parse(byte[] body, int offset) {
        Jt809Bytes.checkRange(body, offset, BODY_LENGTH);
        int index = offset;
        int posEncrypt = Jt809Bytes.unsignedByte(body, index);
        index += POS_ENCRYPT_LENGTH;
        int day = Jt809Bytes.unsignedByte(body, index);
        index += DAY_LENGTH;
        int month = Jt809Bytes.unsignedByte(body, index);
        index += MONTH_LENGTH;
        int year = Jt809Bytes.unsignedShort(body, index);
        index += YEAR_LENGTH;
        int hour = Jt809Bytes.unsignedByte(body, index);
        index += TIME_PART_LENGTH;
        int minute = Jt809Bytes.unsignedByte(body, index);
        index += TIME_PART_LENGTH;
        int second = Jt809Bytes.unsignedByte(body, index);
        index += TIME_PART_LENGTH;
        LocalDateTime deviceTime = LocalDateTime.of(year, month, day, hour, minute, second);
        int longitude = Jt809Bytes.signedInt(body, index);
        index += COORDINATE_LENGTH;
        int latitude = Jt809Bytes.signedInt(body, index);
        index += COORDINATE_LENGTH;
        int speed = Jt809Bytes.unsignedShort(body, index);
        index += SPEED_LENGTH;
        int recorderSpeed = Jt809Bytes.unsignedShort(body, index);
        index += SPEED_LENGTH;
        long totalMileage = Jt809Bytes.unsignedInt(body, index);
        index += MILEAGE_LENGTH;
        int direction = Jt809Bytes.unsignedShort(body, index);
        index += DIRECTION_LENGTH;
        int altitude = Jt809Bytes.unsignedShort(body, index);
        index += ALTITUDE_LENGTH;
        long vehicleStatus = Jt809Bytes.unsignedInt(body, index);
        index += STATE_LENGTH;
        long alarmStatus = Jt809Bytes.unsignedInt(body, index);
        return new LocationSample(posEncrypt, deviceTime, longitude, latitude, speed, recorderSpeed,
                totalMileage, direction, altitude, vehicleStatus, alarmStatus);
    }

    public void write(ByteArrayOutputStream out) {
        Jt809Bytes.writeByte(out, posEncrypt);
        Jt809Bytes.writeByte(out, deviceTime.getDayOfMonth());
        Jt809Bytes.writeByte(out, deviceTime.getMonthValue());
        Jt809Bytes.writeShort(out, deviceTime.getYear());
        Jt809Bytes.writeByte(out, deviceTime.getHour());
        Jt809Bytes.writeByte(out, deviceTime.getMinute());
        Jt809Bytes.writeByte(out, deviceTime.getSecond());
        Jt809Bytes.writeInt(out, longitude);
        Jt809Bytes.writeInt(out, latitude);
        Jt809Bytes.writeShort(out, speed);
        Jt809Bytes.writeShort(out, recorderSpeed);
        Jt809Bytes.writeInt(out, totalMileage);
        Jt809Bytes.writeShort(out, direction);
        Jt809Bytes.writeShort(out, altitude);
        Jt809Bytes.writeInt(out, vehicleStatus);
        Jt809Bytes.writeInt(out, alarmStatus);
    }
}

