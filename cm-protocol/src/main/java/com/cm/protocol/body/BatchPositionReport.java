package com.cm.protocol.body;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cm.protocol.MessageBody;
import com.cm.protocol.ProtocolBytes;

/**
 * 0x0704 定位数据批量上传。每一项自带 2 字节长度前缀，内部结构与 0x0200 一致。
 */
public final class BatchPositionReport implements MessageBody {

    public static final int MESSAGE_ID = 0x0704;

    private static final int HEADER_LENGTH = 3;

    private static final int ITEM_LENGTH_FIELD = 2;

    private final int positionCount;

    private final int reportType;

    private final List<PositionReport> positions;

    private BatchPositionReport(int positionCount, int reportType, List<PositionReport> positions) {
        this.positionCount = positionCount;
        this.reportType = reportType;
        this.positions = Collections.unmodifiableList(positions);
    }

    public static BatchPositionReport parse(byte[] body, int offset, int length) {
        ProtocolBytes.checkRange(body, offset, length);
        if (length < HEADER_LENGTH) {
            throw new IllegalArgumentException("batch position body is shorter than " + HEADER_LENGTH);
        }
        int positionCount = ProtocolBytes.unsignedShort(body, offset);
        int reportType = ProtocolBytes.unsignedByte(body, offset + 2);
        int cursor = offset + HEADER_LENGTH;
        int end = offset + length;
        List<PositionReport> positions = new ArrayList<>(positionCount);
        for (int index = 0; index < positionCount; index++) {
            if (cursor + ITEM_LENGTH_FIELD > end) {
                throw new IllegalArgumentException("batch position item header exceeds body boundary");
            }
            int itemLength = ProtocolBytes.unsignedShort(body, cursor);
            cursor += ITEM_LENGTH_FIELD;
            if (cursor + itemLength > end) {
                throw new IllegalArgumentException("batch position item exceeds body boundary");
            }
            positions.add(PositionReport.parse(body, cursor, itemLength));
            cursor += itemLength;
        }
        if (cursor != end) {
            throw new IllegalArgumentException("trailing bytes after batch position items");
        }
        return new BatchPositionReport(positionCount, reportType, positions);
    }

    @Override
    public int messageId() {
        return MESSAGE_ID;
    }

    public int positionCount() {
        return positionCount;
    }

    public int reportType() {
        return reportType;
    }

    public List<PositionReport> positions() {
        return positions;
    }
}
