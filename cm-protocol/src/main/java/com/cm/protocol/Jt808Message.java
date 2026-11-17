package com.cm.protocol;

import java.util.Arrays;

/**
 * 一条已解码的 JT808 报文。分包帧只保留 childPacket，待组包后再解析消息体。
 */
public final class Jt808Message {

    private final MessageHeader header;

    private final MessageBody body;

    private final byte[] childPacket;

    private final byte[] checksum;

    private final String frameHex;

    private final String errorMessage;

    Jt808Message(MessageHeader header, MessageBody body, byte[] childPacket, byte[] checksum,
            String frameHex, String errorMessage) {
        this.header = header;
        this.body = body;
        this.childPacket = childPacket == null ? null : Arrays.copyOf(childPacket, childPacket.length);
        this.checksum = checksum == null ? null : Arrays.copyOf(checksum, checksum.length);
        this.frameHex = frameHex;
        this.errorMessage = errorMessage;
    }

    public MessageHeader header() {
        return header;
    }

    public MessageBody body() {
        return body;
    }

    public byte[] childPacket() {
        return childPacket == null ? null : Arrays.copyOf(childPacket, childPacket.length);
    }

    public byte[] checksum() {
        return checksum == null ? null : Arrays.copyOf(checksum, checksum.length);
    }

    public String frameHex() {
        return frameHex;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean failed() {
        return errorMessage != null;
    }

    public String simNo() {
        return header == null ? "" : header.simNo();
    }
}
