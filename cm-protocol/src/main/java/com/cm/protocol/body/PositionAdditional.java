package com.cm.protocol.body;

import java.util.Arrays;
import java.util.Objects;

import com.cm.protocol.ProtocolBytes;

/**
 * 位置附加信息项。保留原始负载，已知类型提供取值方法，未识别类型不丢弃。
 */
public final class PositionAdditional {

    public static final int MILEAGE = 0x01;

    public static final int OIL = 0x02;

    public static final int RECORDER_SPEED = 0x03;

    private static final int SIGNAL = 0x25;

    private static final int VOLTAGE = 0xE1;

    private static final int SHORT_PAYLOAD = 2;

    private static final int INT_PAYLOAD = 4;

    private final int id;

    private final byte[] payload;

    private PositionAdditional(int id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    public static PositionAdditional of(int id, byte[] payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return new PositionAdditional(id, payload);
    }

    public int id() {
        return id;
    }

    public int length() {
        return payload.length;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public boolean isMileage() {
        return id == MILEAGE && payload.length == INT_PAYLOAD;
    }

    /**
     * 0x02 油量、0x03 记录仪速度、0xE1 电压都是 2 字节无符号数。
     */
    public boolean isUnsignedShortValue() {
        return payload.length == SHORT_PAYLOAD && (id == OIL || id == RECORDER_SPEED || id == VOLTAGE
                || id == SIGNAL);
    }

    public int unsignedShortValue() {
        if (payload.length < SHORT_PAYLOAD) {
            throw new IllegalArgumentException("additional " + id + " is shorter than 2 bytes");
        }
        return ProtocolBytes.unsignedShort(payload, 0);
    }

    public long unsignedIntValue() {
        if (payload.length < INT_PAYLOAD) {
            throw new IllegalArgumentException("additional " + id + " is shorter than 4 bytes");
        }
        return ProtocolBytes.unsignedInt(payload, 0);
    }
}
