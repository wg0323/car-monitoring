package com.cm.infrastructure.telemetry;

import com.cm.domain.alarm.AlarmStateMachine;
import com.cm.domain.alarm.AlarmTransition;
import com.cm.infrastructure.persistence.entity.CmAlarmRecord;
import com.cm.infrastructure.service.CmAlarmRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警落库。位运算与开 / 关判定在 cm-domain，这里只负责把跳变写成 cm_alarm_record。
 *
 * <p>每个 SIM 只缓存「库里未结束的 alarm_type」，开 / 关判定不依赖进程内存里的上一帧：
 * 同一队列可能存在多个竞争消费者，网关也会重启，上一帧状态不一定是本实例看到的那个。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmRecordWriter {

    private static final int MAX_CACHE_ENTRIES = 100_000;

    private static final String CHANNEL_TERMINAL = "TERMINAL";

    private static final String CHANNEL_PLATFORM = "PLATFORM";

    private static final String ALARM_TYPE_PARKING = "Parking";

    private static final String ALARM_SOURCE_PLATFORM = "platform_alarm";

    private static final String PARKING_DESCRIPTION = "停车";

    private static final String STATUS_NEW = "New";

    private static final String EVENT_KEY_FORMAT = "ALM-T-%s-%s-%d";

    private static final String PARKING_EVENT_KEY_FORMAT = "ALM-P-%s-%s-%d";

    /** 速度原始值是 0.1 km/h；旧系统以 velocity < 1 视为停车。 */
    private static final int PARKING_SPEED_THRESHOLD = 10;

    /** 经纬度原始值是真实角度的 1e6 倍。 */
    private static final BigDecimal COORDINATE_SCALE = BigDecimal.valueOf(1_000_000L);

    private static final int COORDINATE_DIGITS = 6;

    /** 速度、里程、油量原始值都是 0.1 单位。 */
    private static final BigDecimal TENTH_UNIT = BigDecimal.TEN;

    private final CmAlarmRecordService alarmRecordService;

    private final TelemetryMetrics metrics;

    private final TelemetryProperties properties;

    private final Map<String, SimState> states = new ConcurrentHashMap<>();

    public void apply(PositionEvent event, VehicleBinding binding) {
        PositionSample sample = event.sample();
        if (sample == null) {
            return;
        }
        SimState state = stateOf(event.simNo());
        List<AlarmTransition> transitions = AlarmStateMachine.diff(
                sample.alarmFlag(), sample.status(), state.openTypes);
        for (AlarmTransition transition : transitions) {
            if (transition.opening()) {
                open(event, binding, sample, transition, state);
            } else {
                close(event, sample, transition, state);
            }
        }
        applyParking(event, binding, sample, state);
    }

    private SimState stateOf(String simNo) {
        SimState cached = states.get(simNo);
        if (cached != null) {
            return cached;
        }
        if (states.size() >= MAX_CACHE_ENTRIES) {
            states.clear();
        }
        SimState created = new SimState(
                new HashSet<>(alarmRecordService.openAlarmTypes(simNo, CHANNEL_TERMINAL)),
                alarmRecordService.openAlarmTypes(simNo, CHANNEL_PLATFORM).contains(ALARM_TYPE_PARKING));
        states.put(simNo, created);
        return created;
    }

    private void open(PositionEvent event, VehicleBinding binding, PositionSample sample,
                      AlarmTransition transition, SimState state) {
        CmAlarmRecord record = new CmAlarmRecord();
        record.setEventKey(String.format(EVENT_KEY_FORMAT, event.simNo(), transition.alarmType(),
                Instant.parse(event.deviceTime()).getEpochSecond()));
        record.setAlarmChannel(CHANNEL_TERMINAL);
        record.setCompanyId(binding.companyId());
        record.setDepartmentId(binding.departmentId());
        record.setVehicleId(binding.vehicleId());
        record.setPlateNo(binding.plateNo());
        record.setSimNo(event.simNo());
        record.setAlarmSource(transition.alarmSource());
        record.setAlarmType(transition.alarmType());
        record.setDescription(transition.description());
        record.setStartTime(deviceTime(event));
        record.setLatitude(coordinate(sample.latitude()));
        record.setLongitude(coordinate(sample.longitude()));
        record.setMileage1(tenth(sample.mileage()));
        record.setGas1(tenth(sample.oil()));
        record.setVelocity(tenth(sample.speed()));
        record.setAlarmStatus(STATUS_NEW);
        if (alarmRecordService.tryOpenAlarm(record)) {
            metrics.alarmOpened();
        } else {
            metrics.alarmDuplicate();
        }
        // 无论本次是否真正插入，库里此刻确实存在未结束记录，后续判断都以库里的行为准。
        state.openTypes.add(transition.alarmType());
    }

    private void close(PositionEvent event, PositionSample sample, AlarmTransition transition, SimState state) {
        int affected = alarmRecordService.closeOpenAlarm(event.simNo(), transition.alarmType(), CHANNEL_TERMINAL,
                deviceTime(event), coordinate(sample.latitude()), coordinate(sample.longitude()),
                tenth(sample.mileage()), tenth(sample.oil()));
        if (affected > 0) {
            metrics.alarmClosed();
        } else {
            metrics.alarmCloseMissed();
            log.debug("关户未命中，可能已由其他实例关闭, simNo={}, alarmType={}",
                    event.simNo(), transition.alarmType());
        }
        state.openTypes.remove(transition.alarmType());
    }

    /**
     * 平台停车报警：与旧系统 newalarm 同口径，速度低于 1 km/h 开户、恢复行驶关户。
     * 开关判定只看当前帧与库里的未结束记录，多实例竞争消费不会重复开户。
     */
    private void applyParking(PositionEvent event, VehicleBinding binding, PositionSample sample, SimState state) {
        boolean parked = sample.speed() < PARKING_SPEED_THRESHOLD;
        if (parked && !state.parkingOpen) {
            openParking(event, binding, sample, state);
        } else if (!parked && state.parkingOpen) {
            closeParking(event, sample, state);
        }
    }

    private void openParking(PositionEvent event, VehicleBinding binding, PositionSample sample, SimState state) {
        CmAlarmRecord record = new CmAlarmRecord();
        record.setEventKey(String.format(PARKING_EVENT_KEY_FORMAT, event.simNo(), ALARM_TYPE_PARKING,
                Instant.parse(event.deviceTime()).getEpochSecond()));
        record.setAlarmChannel(CHANNEL_PLATFORM);
        record.setCompanyId(binding.companyId());
        record.setDepartmentId(binding.departmentId());
        record.setVehicleId(binding.vehicleId());
        record.setPlateNo(binding.plateNo());
        record.setSimNo(event.simNo());
        record.setAlarmSource(ALARM_SOURCE_PLATFORM);
        record.setAlarmType(ALARM_TYPE_PARKING);
        record.setDescription(PARKING_DESCRIPTION);
        record.setStartTime(deviceTime(event));
        record.setLatitude(coordinate(sample.latitude()));
        record.setLongitude(coordinate(sample.longitude()));
        record.setMileage1(tenth(sample.mileage()));
        record.setGas1(tenth(sample.oil()));
        record.setVelocity(tenth(sample.speed()));
        record.setAlarmStatus(STATUS_NEW);
        if (alarmRecordService.tryOpenAlarm(record)) {
            metrics.alarmOpened();
        } else {
            metrics.alarmDuplicate();
        }
        state.parkingOpen = true;
    }

    private void closeParking(PositionEvent event, PositionSample sample, SimState state) {
        int affected = alarmRecordService.closeOpenAlarm(event.simNo(), ALARM_TYPE_PARKING, CHANNEL_PLATFORM,
                deviceTime(event), coordinate(sample.latitude()), coordinate(sample.longitude()),
                tenth(sample.mileage()), tenth(sample.oil()));
        if (affected > 0) {
            metrics.alarmClosed();
        } else {
            metrics.alarmCloseMissed();
            log.debug("停车关户未命中，可能已由其他实例关闭, simNo={}", event.simNo());
        }
        state.parkingOpen = false;
    }

    /**
     * 报警时间用设备时间，不用接入时间：回放历史报文时时长才是设备实际持续时长。
     */
    private LocalDateTime deviceTime(PositionEvent event) {
        ZoneId zone = ZoneId.of(properties.getTime().getZone());
        return LocalDateTime.ofInstant(Instant.parse(event.deviceTime()), zone);
    }

    private static BigDecimal coordinate(int raw) {
        return BigDecimal.valueOf(raw).divide(COORDINATE_SCALE, COORDINATE_DIGITS, RoundingMode.HALF_UP);
    }

    private static BigDecimal tenth(long raw) {
        return BigDecimal.valueOf(raw).divide(TENTH_UNIT, 2, RoundingMode.HALF_UP);
    }

    private static final class SimState {

        private final Set<String> openTypes;

        private boolean parkingOpen;

        private SimState(Set<String> openTypes, boolean parkingOpen) {
            this.openTypes = openTypes;
            this.parkingOpen = parkingOpen;
        }
    }
}
