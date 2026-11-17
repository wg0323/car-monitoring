package com.cm.protocol;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cm.protocol.body.AuthenticationRequest;
import com.cm.protocol.body.BatchPositionReport;
import com.cm.protocol.body.GenericAck;
import com.cm.protocol.body.PositionAdditional;
import com.cm.protocol.body.PositionQueryAck;
import com.cm.protocol.body.PositionReport;
import com.cm.protocol.body.RegistrationRequest;

/**
 * JT808 上行报文解码。解析失败不抛异常，而是把错误记入返回对象，保证接入链路可以继续处理同批其他帧。
 */
public final class Jt808Decoder {

    private static final byte PRIVATE_FRAME_PREFIX = 0x24;

    private static final int PRIVATE_BODY_LENGTH = 31;

    private static final int PRIVATE_SIM_LENGTH = 5;

    private static final int PRIVATE_TIME_LENGTH = 6;

    private static final int PRIVATE_LATITUDE_LENGTH = 4;

    private static final int PRIVATE_LONGITUDE_LENGTH = 5;

    private static final int PRIVATE_SPEED_DIRECTION_LENGTH = 3;

    private static final int PRIVATE_TEMPERATURE_LENGTH = 1;

    private static final int PRIVATE_SERIAL_OFFSET = 29;

    private static final int ACC_STATUS_INDEX = 26;

    private static final int ACC_MASK = 0x04;

    private static final int SPEED_DIRECTION_DIGITS = 3;

    private static final int COORDINATE_SCALE = 1_000_000;

    private static final int MINUTE_DIVISOR = 600_000;

    private static final int FRACTION_DIGITS = 6;

    private static final int BYTE_HEX_DIGITS = 2;

    private static final int BYTE_MASK = 0xFF;

    private static final int TIMEZONE_OFFSET_HOURS = 8;

    private static final int HOURS_PER_DAY = 24;

    private static final int CHECKSUM_LENGTH = 1;

    private static final String ERROR_CHECKSUM = "校验码不正确";

    private static final String ERROR_PARSE = "解析异常";

    private Jt808Decoder() {
    }

    public static Jt808Message decode(byte[] frame) {
        String frameHex = toHex(frame);
        try {
            byte[] inner = FrameEscaper.stripDelimiters(frame);
            byte[] unescaped = FrameEscaper.unescape(inner);
            if (unescaped.length <= CHECKSUM_LENGTH) {
                throw new IllegalArgumentException("frame carries no header");
            }
            int contentLength = unescaped.length - CHECKSUM_LENGTH;
            byte expected = unescaped[contentLength];
            if (!FrameChecksum.matches(unescaped, 0, contentLength, expected)) {
                return new Jt808Message(null, null, null, null, frameHex, ERROR_CHECKSUM);
            }
            MessageHeader header = MessageHeader.parse(unescaped, 0);
            byte[] checksum = new byte[] {expected};
            int bodySize = header.bodySize();
            int bodyOffset = header.length();
            if (bodyOffset + bodySize > contentLength) {
                throw new IllegalArgumentException("declared body size exceeds frame length");
            }
            if (header.packaged()) {
                byte[] childPacket = new byte[bodySize];
                System.arraycopy(unescaped, bodyOffset, childPacket, 0, bodySize);
                return new Jt808Message(header, null, childPacket, checksum, frameHex, null);
            }
            if (bodySize == 0) {
                return new Jt808Message(header, null, null, checksum, frameHex, null);
            }
            try {
                MessageBody body = parseBody(header.messageId(), unescaped, bodyOffset, bodySize);
                return new Jt808Message(header, body, null, checksum, frameHex, null);
            } catch (IllegalArgumentException | IndexOutOfBoundsException | DateTimeException ex) {
                return new Jt808Message(header, null, null, checksum, frameHex, ERROR_PARSE);
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException | DateTimeException ex) {
            return new Jt808Message(null, null, null, null, frameHex, ERROR_PARSE);
        }
    }

    /**
     * 解码 $ 开头的私有定位帧。旧实现把流水号错读成帧头前 2 字节（黄金快照里是 313），
     * 这里改按帧尾 2 字节读取，差异在差分测试中单独断言。
     */
    public static Jt808Message decodePrivate(byte[] frame) {
        String frameHex = toHex(frame);
        try {
            if (frame == null || frame.length <= PRIVATE_BODY_LENGTH || frame[0] != PRIVATE_FRAME_PREFIX) {
                throw new IllegalArgumentException("not a private position frame");
            }
            byte[] body = new byte[PRIVATE_BODY_LENGTH];
            System.arraycopy(frame, 1, body, 0, PRIVATE_BODY_LENGTH);
            String simNo = ProtocolBytes.bcdString(body, 0, PRIVATE_SIM_LENGTH);
            LocalDateTime deviceTime = privateTime(body, PRIVATE_SIM_LENGTH);
            int latitudeOffset = PRIVATE_SIM_LENGTH + PRIVATE_TIME_LENGTH;
            int longitudeOffset = latitudeOffset + PRIVATE_LATITUDE_LENGTH + PRIVATE_TEMPERATURE_LENGTH;
            int speedDirectionOffset = longitudeOffset + PRIVATE_LONGITUDE_LENGTH;
            int latitude = privateCoordinate(body, latitudeOffset, PRIVATE_LATITUDE_LENGTH, 2);
            int longitude = privateCoordinate(body, longitudeOffset, PRIVATE_LONGITUDE_LENGTH, 3);
            String speedDirection = ProtocolBytes.bcdString(body, speedDirectionOffset,
                    PRIVATE_SPEED_DIRECTION_LENGTH);
            int speed = Integer.parseInt(speedDirection.substring(0, SPEED_DIRECTION_DIGITS));
            int course = Integer.parseInt(speedDirection.substring(SPEED_DIRECTION_DIGITS));
            int status = (body[ACC_STATUS_INDEX] & ACC_MASK) == 0 ? 0 : 1;
            int serialNo = ProtocolBytes.unsignedShort(body, PRIVATE_SERIAL_OFFSET);
            List<PositionAdditional> additionals = new ArrayList<>();
            PositionReport position = PositionReport.of(0, status, latitude, longitude, 0, speed, course,
                    deviceTime, additionals);
            return new Jt808Message(MessageHeader.forPrivatePosition(simNo, serialNo), position, null, null,
                    frameHex, null);
        } catch (IllegalArgumentException | IndexOutOfBoundsException | DateTimeException ex) {
            return new Jt808Message(null, null, null, null, frameHex, ERROR_PARSE);
        }
    }

    private static MessageBody parseBody(int messageId, byte[] payload, int offset, int length) {
        switch (messageId) {
            case GenericAck.MESSAGE_ID:
                return GenericAck.parse(payload, offset, length);
            case RegistrationRequest.MESSAGE_ID:
                return RegistrationRequest.parse(payload, offset, length);
            case AuthenticationRequest.MESSAGE_ID:
                return AuthenticationRequest.parse(payload, offset, length);
            case PositionReport.MESSAGE_ID:
                return PositionReport.parse(payload, offset, length);
            case PositionQueryAck.MESSAGE_ID:
                return PositionQueryAck.parse(payload, offset, length);
            case BatchPositionReport.MESSAGE_ID:
                return BatchPositionReport.parse(payload, offset, length);
            default:
                throw new IllegalArgumentException("message body " + messageId + " is not decodable yet");
        }
    }

    /**
     * 私有帧时间字段顺序为 时、分、秒、日、月、年，取自北京时间，直接使用不做时区换算。
     */
    private static LocalDateTime privateTime(byte[] body, int offset) {
        int hour = ProtocolBytes.bcdByte(body[offset]);
        int minute = ProtocolBytes.bcdByte(body[offset + 1]);
        int second = ProtocolBytes.bcdByte(body[offset + 2]);
        int day = ProtocolBytes.bcdByte(body[offset + 3]);
        int month = ProtocolBytes.bcdByte(body[offset + 4]);
        int year = 2000 + ProtocolBytes.bcdByte(body[offset + 5]);
        int localHour = hour + TIMEZONE_OFFSET_HOURS;
        if (localHour >= HOURS_PER_DAY) {
            localHour -= HOURS_PER_DAY;
        }
        return LocalDateTime.of(year, month, day, localHour, minute, second);
    }

    /**
     * 私有帧坐标是 BCD 度分：前 degreeDigits 位为整数度，后 6 位为分，除以 600000 得到度。
     */
    private static int privateCoordinate(byte[] body, int offset, int length, int degreeDigits) {
        String bcd = ProtocolBytes.bcdString(body, offset, length);
        int degrees = Integer.parseInt(bcd.substring(0, degreeDigits));
        int minutes = Integer.parseInt(bcd.substring(degreeDigits, degreeDigits + FRACTION_DIGITS));
        long fraction = (long) minutes * COORDINATE_SCALE / MINUTE_DIVISOR;
        return (int) (degrees * (long) COORDINATE_SCALE + fraction);
    }

    private static String toHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder hex = new StringBuilder(data.length * BYTE_HEX_DIGITS);
        for (byte value : data) {
            hex.append(String.format("%02x", value & BYTE_MASK));
        }
        return hex.toString();
    }
}
