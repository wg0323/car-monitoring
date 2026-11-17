package com.cm.protocol;

import java.io.ByteArrayOutputStream;

/**
 * JT808 帧转义。0x7E 转义为 7D 02，0x7D 转义为 7D 01；校验码与标识位不参与转义。
 */
public final class FrameEscaper {

    private FrameEscaper() {
    }

    public static byte[] escape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + data.length / Byte.SIZE);
        for (byte value : data) {
            if (value == FrameConstants.ESCAPE_PREFIX) {
                out.write(FrameConstants.ESCAPE_PREFIX);
                out.write(FrameConstants.ESCAPED_PREFIX);
            } else if (value == FrameConstants.FRAME_DELIMITER) {
                out.write(FrameConstants.ESCAPE_PREFIX);
                out.write(FrameConstants.ESCAPED_DELIMITER);
            } else {
                out.write(value);
            }
        }
        return out.toByteArray();
    }

    /**
     * 还原转义字节。0x7D 后必须紧跟 0x01 或 0x02，其他组合视为非法帧。
     */
    public static byte[] unescape(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        int index = 0;
        while (index < data.length) {
            byte value = data[index];
            if (value != FrameConstants.ESCAPE_PREFIX) {
                out.write(value);
                index++;
                continue;
            }
            if (index + 1 >= data.length) {
                throw new IllegalArgumentException("dangling escape prefix at frame tail");
            }
            byte escaped = data[index + 1];
            if (escaped == FrameConstants.ESCAPED_PREFIX) {
                out.write(FrameConstants.ESCAPE_PREFIX);
            } else if (escaped == FrameConstants.ESCAPED_DELIMITER) {
                out.write(FrameConstants.FRAME_DELIMITER);
            } else {
                throw new IllegalArgumentException("illegal escape sequence 7D " + escaped);
            }
            index += 2;
        }
        return out.toByteArray();
    }

    /**
     * 去掉首尾标识位并校验边界；返回仍带转义的帧内字节。
     */
    public static byte[] stripDelimiters(byte[] frame) {
        if (frame == null || frame.length < 2) {
            throw new IllegalArgumentException("frame is too short to hold delimiters");
        }
        if (frame[0] != FrameConstants.FRAME_DELIMITER
                || frame[frame.length - 1] != FrameConstants.FRAME_DELIMITER) {
            throw new IllegalArgumentException("frame must be delimited by 0x7E");
        }
        byte[] inner = new byte[frame.length - 2];
        System.arraycopy(frame, 1, inner, 0, inner.length);
        return inner;
    }

    /**
     * 转义帧内字节并补上首尾标识位。
     */
    public static byte[] wrap(byte[] escapedPayload) {
        byte[] frame = new byte[escapedPayload.length + 2];
        frame[0] = FrameConstants.FRAME_DELIMITER;
        System.arraycopy(escapedPayload, 0, frame, 1, escapedPayload.length);
        frame[frame.length - 1] = FrameConstants.FRAME_DELIMITER;
        return frame;
    }
}
