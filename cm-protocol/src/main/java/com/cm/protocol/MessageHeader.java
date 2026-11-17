package com.cm.protocol;

import java.io.ByteArrayOutputStream;

/**
 * JT808 消息头。终端手机号按 BCD[6] 读取后去掉首位填充，得到库里 11 位 simNo 口径。
 */
public final class MessageHeader {

    private static final int BODY_SIZE_MASK = 0x03FF;

    private static final int PACKAGE_FLAG = 0x2000;

    private static final int HEADER_LENGTH = 12;

    private static final int PACKAGE_HEADER_LENGTH = 16;

    private static final int SIM_BCD_LENGTH = 6;

    private static final int PLAIN_SIM_LENGTH = 11;

    private final int messageId;

    private final int bodyProperty;

    private final String simNo;

    private final int serialNo;

    private final int totalPackets;

    private final int packetNo;

    private MessageHeader(int messageId, int bodyProperty, String simNo, int serialNo,
            int totalPackets, int packetNo) {
        this.messageId = messageId;
        this.bodyProperty = bodyProperty;
        this.simNo = simNo;
        this.serialNo = serialNo;
        this.totalPackets = totalPackets;
        this.packetNo = packetNo;
    }

    public static MessageHeader parse(byte[] payload, int offset) {
        int messageId = ProtocolBytes.unsignedShort(payload, offset);
        int bodyProperty = ProtocolBytes.unsignedShort(payload, offset + 2);
        String bcdSim = ProtocolBytes.bcdString(payload, offset + 4, SIM_BCD_LENGTH);
        String simNo = bcdSim.length() > PLAIN_SIM_LENGTH ? bcdSim.substring(1) : bcdSim;
        int cursor = offset + 4 + SIM_BCD_LENGTH;
        int serialNo = ProtocolBytes.unsignedShort(payload, cursor);
        cursor += 2;
        int totalPackets = 0;
        int packetNo = 0;
        if ((bodyProperty & PACKAGE_FLAG) != 0) {
            totalPackets = ProtocolBytes.unsignedShort(payload, cursor);
            packetNo = ProtocolBytes.unsignedShort(payload, cursor + 2);
        }
        return new MessageHeader(messageId, bodyProperty, simNo, serialNo, totalPackets, packetNo);
    }

    /**
     * 为平台下行报文构造消息头；分包标记由消息体长度与业务决定，下行默认不分包。
     */
    public static MessageHeader forDownlink(int messageId, String simNo, int serialNo, int bodySize) {
        return new MessageHeader(messageId, bodySize & BODY_SIZE_MASK, simNo, serialNo, 0, 0);
    }

    /**
     * 私有 $ 帧没有标准消息头，这里用等价字段占位，便于上下游统一处理。
     */
    public static MessageHeader forPrivatePosition(String simNo, int serialNo) {
        return new MessageHeader(0x0200, 0, simNo, serialNo, 0, 0);
    }

    public byte[] writeToBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_LENGTH);
        ProtocolBytes.writeShort(out, messageId);
        ProtocolBytes.writeShort(out, bodyProperty);
        byte[] simBytes = toSimBcd(simNo);
        out.write(simBytes, 0, simBytes.length);
        ProtocolBytes.writeShort(out, serialNo);
        if (packaged()) {
            ProtocolBytes.writeShort(out, totalPackets);
            ProtocolBytes.writeShort(out, packetNo);
        }
        return out.toByteArray();
    }

    public int length() {
        return packaged() ? PACKAGE_HEADER_LENGTH : HEADER_LENGTH;
    }

    public boolean packaged() {
        return (bodyProperty & PACKAGE_FLAG) != 0;
    }

    public int bodySize() {
        return bodyProperty & BODY_SIZE_MASK;
    }

    public int messageId() {
        return messageId;
    }

    public int bodyProperty() {
        return bodyProperty;
    }

    public String simNo() {
        return simNo;
    }

    public int serialNo() {
        return serialNo;
    }

    public int totalPackets() {
        return totalPackets;
    }

    public int packetNo() {
        return packetNo;
    }

    /**
     * 左补 0 到 12 位 BCD，与旧编码器写头的口径一致。
     */
    private static byte[] toSimBcd(String simNo) {
        if (simNo == null || simNo.isEmpty()) {
            throw new IllegalArgumentException("simNo must not be empty");
        }
        StringBuilder padded = new StringBuilder(simNo);
        while (padded.length() < SIM_BCD_LENGTH * 2) {
            padded.insert(0, '0');
        }
        if (padded.length() != SIM_BCD_LENGTH * 2) {
            throw new IllegalArgumentException("simNo is longer than 12 BCD digits");
        }
        byte[] bytes = new byte[SIM_BCD_LENGTH];
        for (int index = 0; index < SIM_BCD_LENGTH; index++) {
            int high = padded.charAt(index * 2) - '0';
            int low = padded.charAt(index * 2 + 1) - '0';
            if (high < 0 || high > 9 || low < 0 || low > 9) {
                throw new IllegalArgumentException("simNo must be decimal digits");
            }
            bytes[index] = (byte) (high << 4 | low);
        }
        return bytes;
    }
}
