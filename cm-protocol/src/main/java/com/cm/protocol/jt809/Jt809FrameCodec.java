package com.cm.protocol.jt809;

import java.io.ByteArrayOutputStream;

/**
 * JT809 整帧编解码。长度字段等于全帧字节数（含首尾标识位），校验范围是 5B 与 5D 之间的字节，
 * 两者都不参与转义；这两条口径直接决定与旧平台能否互通，不能按“看起来合理”改。
 */
public final class Jt809FrameCodec {

    private static final int BYTE_MASK = 0xFF;

    private static final int CRC_SHIFT = 8;

    private Jt809FrameCodec() {
    }

    public static byte[] encode(int messageId, long messageSn, long centerId, byte[] body) {
        return encode(messageId, messageSn, centerId, Jt809Constants.LEGACY_VERSION, body);
    }

    public static byte[] encode(int messageId, long messageSn, long centerId, byte[] version, byte[] body) {
        byte[] payloadBody = body == null ? new byte[0] : body;
        if (payloadBody.length > Jt809Constants.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("jt809 body too long: " + payloadBody.length);
        }
        if (version == null || version.length != Jt809Constants.VERSION_LENGTH) {
            throw new IllegalArgumentException("jt809 version must be 3 bytes");
        }
        ByteArrayOutputStream header = new ByteArrayOutputStream(payloadBody.length + Jt809Constants.HEADER_LENGTH);
        Jt809Bytes.writeInt(header, payloadBody.length + Jt809Constants.FIXED_OVERHEAD);
        Jt809Bytes.writeInt(header, messageSn);
        Jt809Bytes.writeShort(header, messageId);
        Jt809Bytes.writeInt(header, centerId);
        header.writeBytes(version);
        Jt809Bytes.writeByte(header, Jt809Constants.UNENCRYPTED);
        Jt809Bytes.writeInt(header, Jt809Constants.NO_ENCRYPT_KEY);
        header.writeBytes(payloadBody);

        byte[] withoutCrc = header.toByteArray();
        int crc = Jt809Crc.compute(withoutCrc, 0, withoutCrc.length);
        byte[] payload = new byte[withoutCrc.length + Jt809Constants.CRC_LENGTH];
        System.arraycopy(withoutCrc, 0, payload, 0, withoutCrc.length);
        payload[withoutCrc.length] = (byte) (crc >>> CRC_SHIFT);
        payload[withoutCrc.length + 1] = (byte) crc;

        ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 4);
        frame.write(Jt809Constants.HEAD_FLAG);
        Jt809Escape.escape(payload, 0, payload.length, frame);
        // 发端按未转义长度校验，收端按转义后整帧长度校验，两者不是同一把尺子；
        // 全转义时帧会翻倍，这里按转义后的实际长度再校一次，避免自产帧被自身解码器丢弃。
        if (frame.size() + 1 > Jt809Constants.MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("jt809 escaped frame too long: " + (frame.size() + 1));
        }
        frame.write(Jt809Constants.TAIL_FLAG);
        return frame.toByteArray();
    }

    public static Jt809Frame decode(byte[] frame) {
        if (frame == null || frame.length < Jt809Constants.MIN_FRAME_LENGTH
                || frame.length > Jt809Constants.MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("illegal jt809 frame length: "
                    + (frame == null ? -1 : frame.length));
        }
        if ((frame[0] & BYTE_MASK) != Jt809Constants.HEAD_FLAG
                || (frame[frame.length - 1] & BYTE_MASK) != Jt809Constants.TAIL_FLAG) {
            throw new IllegalArgumentException("jt809 frame must start with 0x5B and end with 0x5D");
        }
        byte[] payload = Jt809Escape.unescape(frame, 1, frame.length - 2);
        if (payload.length < Jt809Constants.HEADER_LENGTH + Jt809Constants.CRC_LENGTH) {
            throw new IllegalArgumentException("jt809 frame shorter than header");
        }
        int declared = Jt809Bytes.signedInt(payload, 0);
        if (declared != payload.length + 2) {
            throw new IllegalArgumentException("jt809 declared length mismatch: declared=" + declared
                    + ", actual=" + (payload.length + 2));
        }
        int crcIndex = payload.length - Jt809Constants.CRC_LENGTH;
        int expected = ((payload[crcIndex] & BYTE_MASK) << CRC_SHIFT) | (payload[crcIndex + 1] & BYTE_MASK);
        int actual = Jt809Crc.compute(payload, 0, crcIndex);
        if (expected != actual) {
            throw new IllegalArgumentException("jt809 frame checksum mismatch");
        }
        long messageSn = Jt809Bytes.unsignedInt(payload, 4);
        int messageId = Jt809Bytes.unsignedShort(payload, 8);
        long centerId = Jt809Bytes.unsignedInt(payload, 10);
        byte[] version = Jt809Bytes.readFixedBytes(payload, 14, Jt809Constants.VERSION_LENGTH);
        int encryptFlag = Jt809Bytes.unsignedByte(payload, 14 + Jt809Constants.VERSION_LENGTH);
        long encryptKey = Jt809Bytes.unsignedInt(payload, 15 + Jt809Constants.VERSION_LENGTH);
        int bodyOffset = Jt809Constants.HEADER_LENGTH;
        byte[] body = Jt809Bytes.readFixedBytes(payload, bodyOffset, crcIndex - bodyOffset);
        return new Jt809Frame(messageId, messageSn, centerId, version, encryptFlag, encryptKey, body);
    }

    /** 未转义帧的总字节数，即长度字段的声明值；不足 6 字节时视为噪声。 */
    public static int declaredLength(byte[] data, int offset) {
        if (data.length - offset < Jt809Constants.CRC_LENGTH + 4) {
            return -1;
        }
        return Jt809Bytes.signedInt(data, offset + 1);
    }
}

