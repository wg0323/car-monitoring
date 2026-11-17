package com.cm.integration.forward;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;
import com.cm.infrastructure.telemetry.VehicleBinding;
import com.cm.infrastructure.telemetry.VehicleBindingResolver;
import com.cm.integration.config.Gov809Properties;
import com.cm.protocol.jt809.body.LocationSample;
import com.cm.protocol.jt809.body.VehicleLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 把定位事件转成 0x1200/0x1202 消息体。808 的速度与里程单位是 0.1，
 * 809 同名字段是整数公里（/小时、/公里），这里统一降精度；坐标两边都是 1e-6 度，直接透传。
 * 未绑定车牌的车辆不转发：809 报文的定位头必须带 21 字节车牌，无法用 SIM 代替。
 */
@Component
@RequiredArgsConstructor
public class Gov809FrameBuilder {

    private static final int TENTH_TO_UNIT = 10;

    private static final int POSITION_NOT_ENCRYPTED = 0;

    private final VehicleBindingResolver bindingResolver;

    private final Gov809Properties properties;

    public BuiltFrame build(PositionEvent event) {
        VehicleBinding binding = bindingResolver.resolve(event.simNo());
        if (binding.plateNo().isEmpty()) {
            return null;
        }
        PositionSample source = event.sample();
        LocationSample sample = new LocationSample(POSITION_NOT_ENCRYPTED, deviceTime(event),
                source.longitude(), source.latitude(), toUnit(source.speed()),
                toUnit(source.recorderSpeed()), toUnit(source.mileage()), source.course(),
                source.altitude(), source.status(), source.alarmFlag());
        byte[] body = new VehicleLocation(binding.plateNo(), binding.plateColor(), sample).encode();
        return new BuiltFrame(binding.plateNo(), binding.plateColor(), body);
    }

    private LocalDateTime deviceTime(PositionEvent event) {
        String time = event.effectiveDeviceTime() == null || event.effectiveDeviceTime().isBlank()
                ? event.deviceTime() : event.effectiveDeviceTime();
        return LocalDateTime.ofInstant(Instant.parse(time), ZoneId.of(properties.getZone()));
    }

    private static int toUnit(long tenth) {
        return (int) (tenth / TENTH_TO_UNIT);
    }

    /** 已编码的定位帧：车牌与颜色随报文保存，补发时直接复用。 */
    public record BuiltFrame(String plateNo, int plateColor, byte[] body) {
    }
}
 
