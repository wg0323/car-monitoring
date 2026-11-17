package com.cm.protocol;

/**
 * JT808 帧界定与转义常量。数值口径来自 P0 冻结的 29 帧黄金报文与解码快照。
 */
public final class FrameConstants {

    public static final byte FRAME_DELIMITER = 0x7E;

    public static final byte ESCAPE_PREFIX = 0x7D;

    public static final byte ESCAPED_PREFIX = 0x01;

    public static final byte ESCAPED_DELIMITER = 0x02;

    public static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private FrameConstants() {
    }
}
