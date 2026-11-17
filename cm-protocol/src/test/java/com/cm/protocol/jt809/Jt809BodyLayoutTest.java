package com.cm.protocol.jt809;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cm.protocol.jt809.body.LocationSample;
import com.cm.protocol.jt809.body.UrgeTodoAck;
import com.cm.protocol.jt809.body.UrgeTodoRequest;
import com.cm.protocol.jt809.body.VehicleLocation;
import com.cm.protocol.jt809.body.VehicleLocationBatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务报文布局：字段顺序与长度全部取自旧实现，不按规范臆断。
 * 0x1401 对应 UpWarnMsgUrgeToDoAck，0x1202/0x1203 对应 UpExgMsgRealLocation/HistoryLocation。
 */
class Jt809BodyLayoutTest {

    private static final String PLATE = "测A00001";

    private static final int PLATE_COLOR = 2;

    private static final LocalDateTime DEVICE_TIME = LocalDateTime.of(2026, 11, 15, 12, 3, 21);

    @Test
    @DisplayName("0x1401 督办应答体为 33 字节，后续长度 5，督办 ID 与结果在末尾")
    void should_layout_urge_todo_ack() {
        UrgeTodoAck ack = new UrgeTodoAck(PLATE, PLATE_COLOR, 2147483646L, 1);

        byte[] body = ack.encode();

        assertEquals(33, body.length);
        assertEquals(0x1401, Jt809Bytes.unsignedShort(body, 22));
        assertEquals(5, Jt809Bytes.signedInt(body, 24));
        assertEquals(2147483646L, Jt809Bytes.unsignedInt(body, 28));
        assertEquals(1, Jt809Bytes.unsignedByte(body, 32));
        assertEquals(ack, UrgeTodoAck.parse(body));
    }

    @Test
    @DisplayName("0x1202 实时定位体为 64 字节，日期按数值写而非 BCD")
    void should_layout_real_location() {
        LocationSample sample = new LocationSample(0, DEVICE_TIME, 120000000, 30000000,
                60, 62, 1234L, 180, 35, 1L, 0L);

        byte[] body = new VehicleLocation(PLATE, PLATE_COLOR, sample).encode();

        assertEquals(64, body.length);
        assertEquals(Jt809Constants.SUB_TYPE_REAL_LOCATION, Jt809Bytes.unsignedShort(body, 22));
        assertEquals(36, Jt809Bytes.signedInt(body, 24));
        assertEquals(0, Jt809Bytes.unsignedByte(body, 28));
        assertEquals(15, Jt809Bytes.unsignedByte(body, 29));
        assertEquals(11, Jt809Bytes.unsignedByte(body, 30));
        assertEquals(2026, Jt809Bytes.unsignedShort(body, 31));
        assertEquals(12, Jt809Bytes.unsignedByte(body, 33));
        assertEquals(3, Jt809Bytes.unsignedByte(body, 34));
        assertEquals(21, Jt809Bytes.unsignedByte(body, 35));
        assertEquals(120000000, Jt809Bytes.signedInt(body, 36));
        assertEquals(new VehicleLocation(PLATE, PLATE_COLOR, sample), VehicleLocation.parse(body));
    }

    @Test
    @DisplayName("0x1203 补发定位体 = 29 + 36n，后续长度 = 1 + 36n")
    void should_layout_history_location() {
        LocationSample first = new LocationSample(0, DEVICE_TIME, 1, 2, 3, 4, 5L, 6, 7, 8L, 9L);
        LocationSample second = new LocationSample(0, DEVICE_TIME.plusSeconds(30), 10, 20,
                30, 40, 50L, 60, 70, 80L, 90L);
        VehicleLocationBatch batch = new VehicleLocationBatch(PLATE, PLATE_COLOR, List.of(first, second));

        byte[] body = batch.encode();

        assertEquals(29 + Jt809Constants.LOCATION_SAMPLE_LENGTH * 2, body.length);
        assertEquals(Jt809Constants.SUB_TYPE_HISTORY_LOCATION, Jt809Bytes.unsignedShort(body, 22));
        assertEquals(1 + Jt809Constants.LOCATION_SAMPLE_LENGTH * 2, Jt809Bytes.signedInt(body, 24));
        assertEquals(2, Jt809Bytes.unsignedByte(body, 28));
        assertEquals(batch, VehicleLocationBatch.parse(body));
    }

    @Test
    @DisplayName("0x1400 督办请求体为 118 字节，后续长度 90，可原样往返")
    void should_layout_urge_todo_request() {
        UrgeTodoRequest request = new UrgeTodoRequest(PLATE, PLATE_COLOR, 1, 2,
                1763107200L, 2147483646L, 1763114400L, 3, "张三", "13800000000", "a@b.com");

        byte[] body = request.encode();

        assertEquals(118, body.length);
        assertEquals(Jt809Constants.SUB_TYPE_URGE_TODO_REQUEST, Jt809Bytes.unsignedShort(body, 22));
        assertEquals(90, Jt809Bytes.signedInt(body, 24));
        assertEquals(request, UrgeTodoRequest.parse(body));
    }

    @Test
    @DisplayName("子类型不匹配时拒绝解析，避免把督办当定位")
    void should_reject_mismatched_sub_type() {
        byte[] body = new UrgeTodoAck(PLATE, PLATE_COLOR, 1L, 0).encode();
        body[23] = 0x02;

        assertThrows(IllegalArgumentException.class, () -> UrgeTodoAck.parse(body));
    }
}

