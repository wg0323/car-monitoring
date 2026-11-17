package com.cm.integration.forward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;
import com.cm.infrastructure.telemetry.VehicleBinding;
import com.cm.infrastructure.telemetry.VehicleBindingResolver;
import com.cm.integration.config.Gov809Properties;
import com.cm.protocol.jt809.body.LocationSample;
import com.cm.protocol.jt809.body.VehicleLocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

/**
 * 定位事件到 0x1202 的映射：单位换算、坐标透传、时间取 effectiveDeviceTime，
 * 未绑定车牌的车辆不转发。
 */
@ExtendWith(MockitoExtension.class)
class Gov809FrameBuilderTest {

    private static final String SIM_NO = "13912345678";

    private static final String PLATE_NO = "测A00001";

    private static final int PLATE_COLOR = 2;

    @Mock
    private VehicleBindingResolver bindingResolver;

    private Gov809FrameBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new Gov809FrameBuilder(bindingResolver, new Gov809Properties());
    }

    @Test
    @DisplayName("速度与里程降 10 倍精度，坐标与状态原样透传")
    void should_build_real_location_body() {
        when(bindingResolver.resolve(SIM_NO))
                .thenReturn(new VehicleBinding(1L, 0L, PLATE_NO, PLATE_COLOR, 0L));

        Gov809FrameBuilder.BuiltFrame frame = builder.build(event("2026-11-15T04:03:21Z", 600L, 12340L));

        assertEquals(PLATE_NO, frame.plateNo());
        assertEquals(PLATE_COLOR, frame.plateColor());
        VehicleLocation location = VehicleLocation.parse(frame.body());
        LocationSample sample = location.sample();
        assertEquals(123456789, sample.longitude());
        assertEquals(30000000, sample.latitude());
        assertEquals(60, sample.speed());
        assertEquals(62, sample.recorderSpeed());
        assertEquals(1234L, sample.totalMileage());
        assertEquals(180, sample.direction());
        assertEquals(35, sample.altitude());
        assertEquals(1L, sample.vehicleStatus());
        assertEquals(2L, sample.alarmStatus());
        // effectiveDeviceTime 是 UTC，Asia/Shanghai 下应为 12:03:21。
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 3, 21), sample.deviceTime());
    }

    @Test
    @DisplayName("车辆未绑定车牌时不转发")
    void should_skip_unbound_vehicle() {
        when(bindingResolver.resolve(SIM_NO)).thenReturn(VehicleBinding.empty());

        assertNull(builder.build(event("2026-11-15T04:03:21Z", 600L, 12340L)));
    }

    private PositionEvent event(String effectiveDeviceTime, long speed, long mileage) {
        PositionSample sample = new PositionSample(2, 1, 30000000, 123456789, 35,
                (int) speed, 180, mileage, 0, (int) (speed + 20), 0, 0);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, "evt-1", PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, SIM_NO, 7, effectiveDeviceTime, effectiveDeviceTime,
                effectiveDeviceTime, "NORMAL", "gw-1", "evt-1", 0, sample, "digest");
    }
}
 
