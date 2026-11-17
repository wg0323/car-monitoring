package com.cm.protocol.body;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x0100 终端注册。终端型号存在 8 字节（旧终端）与 20 字节（JT/T 808-2011）两种长度，
 * 按消息体总长判定，判定口径与旧实现一致。
 */
public final class RegistrationRequest implements MessageBody {

    public static final int MESSAGE_ID = 0x0100;

    private static final int LEGACY_MODEL_LENGTH = 8;

    private static final int MODEL_LENGTH = 20;

    private static final int MANUFACTURE_LENGTH = 5;

    private static final int TERMINAL_ID_BYTES = 7;

    private static final int LEGACY_BOUNDARY = 36;

    private static final int LEGACY_CONSUMED = 25;

    private static final int CONSUMED = 37;

    private final int provinceId;

    private final int cityId;

    private final String manufactureId;

    private final String terminalModelNo;

    private final String terminalId;

    private final int plateColor;

    private final String plateNo;

    private RegistrationRequest(int provinceId, int cityId, String manufactureId, String terminalModelNo,
            String terminalId, int plateColor, String plateNo) {
        this.provinceId = provinceId;
        this.cityId = cityId;
        this.manufactureId = manufactureId;
        this.terminalModelNo = terminalModelNo;
        this.terminalId = terminalId;
        this.plateColor = plateColor;
        this.plateNo = plateNo;
    }

    public static RegistrationRequest parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        int cursor = offset;
        int provinceId = ProtocolBytes.unsignedShort(body, cursor);
        int cityId = ProtocolBytes.unsignedShort(body, cursor + 2);
        cursor += Integer.BYTES;
        String manufactureId = ProtocolBytes.gbkString(body, cursor, MANUFACTURE_LENGTH);
        cursor += MANUFACTURE_LENGTH;
        boolean legacy = length < LEGACY_BOUNDARY;
        int modelLength = legacy ? LEGACY_MODEL_LENGTH : MODEL_LENGTH;
        String terminalModelNo = ProtocolBytes.gbkString(body, cursor, modelLength);
        cursor += modelLength;
        String terminalId = ProtocolBytes.gbkString(body, cursor, TERMINAL_ID_BYTES);
        cursor += TERMINAL_ID_BYTES;
        int plateColor = ProtocolBytes.unsignedByte(body, cursor);
        cursor++;
        int consumed = legacy ? LEGACY_CONSUMED : CONSUMED;
        int plateLength = length - consumed;
        if (plateLength < 0) {
            throw new IllegalArgumentException("registration body is shorter than declared");
        }
        String plateNo = ProtocolBytes.gbkString(body, cursor, plateLength);
        return new RegistrationRequest(provinceId, cityId, manufactureId, terminalModelNo, terminalId,
                plateColor, plateNo);
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public int provinceId() {
        return provinceId;
    }

    public int cityId() {
        return cityId;
    }

    public String manufactureId() {
        return manufactureId;
    }

    public String terminalModelNo() {
        return terminalModelNo;
    }

    public String terminalId() {
        return terminalId;
    }

    public int plateColor() {
        return plateColor;
    }

    public String plateNo() {
        return plateNo;
    }
}
