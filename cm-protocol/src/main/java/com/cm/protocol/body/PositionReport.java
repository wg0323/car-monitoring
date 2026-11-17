package com.cm.protocol.body;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x0200 位置信息汇报。时间和附加项以外的字段口径见 JT/T 808-2011 表 20。
 */
public final class PositionReport implements MessageBody {

    public static final int MESSAGE_ID = 0x0200;

    private static final int FIXED_BODY_LENGTH = 28;

    private static final int ADDITIONAL_HEADER_LENGTH = 2;

    private final int alarmFlag;

    private final int status;

    private final int latitude;

    private final int longitude;

    private final int altitude;

    private final int speed;

    private final int course;

    private final LocalDateTime deviceTime;

    private final List<PositionAdditional> additionals;

    private PositionReport(int alarmFlag, int status, int latitude, int longitude, int altitude,
            int speed, int course, LocalDateTime deviceTime, List<PositionAdditional> additionals) {
        this.alarmFlag = alarmFlag;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.speed = speed;
        this.course = course;
        this.deviceTime = deviceTime;
        this.additionals = Collections.unmodifiableList(additionals);
    }

    public static PositionReport parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        if (length < FIXED_BODY_LENGTH) {
            throw new IllegalArgumentException("position body is shorter than " + FIXED_BODY_LENGTH);
        }
        int cursor = offset;
        int alarmFlag = ProtocolBytes.signedInt(body, cursor);
        int status = ProtocolBytes.signedInt(body, cursor + 4);
        int latitude = ProtocolBytes.signedInt(body, cursor + 8);
        int longitude = ProtocolBytes.signedInt(body, cursor + 12);
        int altitude = ProtocolBytes.unsignedShort(body, cursor + 16);
        int speed = ProtocolBytes.unsignedShort(body, cursor + 18);
        int course = ProtocolBytes.unsignedShort(body, cursor + 20);
        LocalDateTime deviceTime = ProtocolBytes.bcdDateTime(body, cursor + 22);
        cursor += FIXED_BODY_LENGTH;
        int end = offset + length;
        List<PositionAdditional> additionals = new ArrayList<>();
        while (cursor + ADDITIONAL_HEADER_LENGTH <= end) {
            int id = ProtocolBytes.unsignedByte(body, cursor);
            int itemLength = ProtocolBytes.unsignedByte(body, cursor + 1);
            cursor += ADDITIONAL_HEADER_LENGTH;
            if (cursor + itemLength > end) {
                throw new IllegalArgumentException("position additional exceeds body boundary");
            }
            byte[] payload = new byte[itemLength];
            System.arraycopy(body, cursor, payload, 0, itemLength);
            additionals.add(PositionAdditional.of(id, payload));
            cursor += itemLength;
        }
        if (cursor != end) {
            throw new IllegalArgumentException("trailing bytes after position additionals");
        }
        return new PositionReport(alarmFlag, status, latitude, longitude, altitude, speed, course,
                deviceTime, additionals);
    }

    /**
     * 私有 $ 帧的字段不在标准消息体内，解码后复用同一位置模型。
     */
    public static PositionReport of(int alarmFlag, int status, int latitude, int longitude, int altitude,
            int speed, int course, LocalDateTime deviceTime, List<PositionAdditional> additionals) {
        Objects.requireNonNull(deviceTime, "deviceTime must not be null");
        Objects.requireNonNull(additionals, "additionals must not be null");
        return new PositionReport(alarmFlag, status, latitude, longitude, altitude, speed, course,
                deviceTime, new ArrayList<>(additionals));
    }

    /**
     * 里程取自 0x01 附加项，缺失时按 0 处理，与旧报表口径一致。
     */
    public long mileage() {
        for (PositionAdditional item : additionals) {
            if (item.isMileage()) {
                return item.unsignedIntValue();
            }
        }
        return 0L;
    }

    public int oil() {
        return unsignedShortOf(PositionAdditional.OIL);
    }

    public int recorderSpeed() {
        return unsignedShortOf(PositionAdditional.RECORDER_SPEED);
    }

    public int signal() {
        return unsignedShortOf(0x25);
    }

    public int voltage() {
        return unsignedShortOf(0xE1);
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public int alarmFlag() {
        return alarmFlag;
    }

    public int status() {
        return status;
    }

    public int latitude() {
        return latitude;
    }

    public int longitude() {
        return longitude;
    }

    public int altitude() {
        return altitude;
    }

    public int speed() {
        return speed;
    }

    public int course() {
        return course;
    }

    public LocalDateTime deviceTime() {
        return deviceTime;
    }

    public List<PositionAdditional> additionals() {
        return additionals;
    }

    private int unsignedShortOf(int targetId) {
        for (PositionAdditional item : additionals) {
            if (item.id() == targetId && item.isUnsignedShortValue()) {
                return item.unsignedShortValue();
            }
        }
        return 0;
    }
}
