package com.cm.admin.service.impl;

import com.cm.admin.converter.TrackConverter;
import com.cm.admin.service.TrackAppService;
import com.cm.admin.support.ReportExcelWriter;
import com.cm.admin.vo.TrackPointVO;
import com.cm.admin.vo.TrackResultVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.domain.track.TrackQuery;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.DataScopeService;
import com.cm.infrastructure.telemetry.PositionTrackRepository;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 历史轨迹实现。车辆必须先落在登录主体的数据范围内，
 * 点集上限与时间跨度在服务端校验，避免整库扫描把管理端拖死。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackAppServiceImpl implements TrackAppService {

    private final DataScopeService dataScopeService;

    private final CmVehicleService vehicleService;

    private final PositionTrackRepository trackRepository;

    private final TelemetryProperties telemetryProperties;

    @Override
    public TrackResultVO query(Long userId, long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        VehicleRow vehicle = requireVehicle(userId, vehicleId);
        requireRange(startTime, endTime);
        List<TrackPointVO> points = loadPoints(vehicle, startTime, endTime);
        log.debug("历史轨迹查询, userId={}, vehicleId={}, rows={}", userId, vehicleId, points.size());
        return TrackConverter.toResultVO(vehicle, points.size(), points);
    }

    @Override
    public void export(Long userId, long vehicleId, LocalDateTime startTime, LocalDateTime endTime,
                       OutputStream outputStream) {
        VehicleRow vehicle = requireVehicle(userId, vehicleId);
        requireRange(startTime, endTime);
        List<TrackPointVO> points = loadPoints(vehicle, startTime, endTime);
        log.info("历史轨迹导出, userId={}, vehicleId={}, rows={}", userId, vehicleId, points.size());
        ReportExcelWriter.write(outputStream, "GPS历史轨迹", TrackPointVO.class, points);
    }

    private List<TrackPointVO> loadPoints(VehicleRow vehicle, LocalDateTime startTime, LocalDateTime endTime) {
        TrackQuery query = new TrackQuery(vehicle.getId(), vehicle.getSimNo(), startTime, endTime);
        long total = trackRepository.count(query);
        if (total > QueryLimits.MAX_TRACK_POINTS) {
            throw new CmBusinessException("轨迹点数 " + total + " 超过上限 " + QueryLimits.MAX_TRACK_POINTS
                    + "，请缩小时间范围");
        }
        ZoneId zone = ZoneId.of(telemetryProperties.getTime().getZone());
        return trackRepository.find(query, QueryLimits.MAX_TRACK_POINTS)
                .stream()
                .map(point -> TrackConverter.toPointVO(point, vehicle, vehicle.getPlateColor(), zone))
                .toList();
    }

    /**
     * 车辆不在数据范围内时按 403 拒绝，不暴露「车辆存在但无权查看」之外的任何信息。
     */
    private VehicleRow requireVehicle(Long userId, long vehicleId) {
        DataScope scope = dataScopeService.resolve(userId);
        List<VehicleRow> vehicles = vehicleService.listInScopeByIds(scope, List.of(vehicleId));
        if (vehicles.isEmpty()) {
            throw new AccessDeniedException("所选车辆不在当前账号的数据范围内");
        }
        VehicleRow vehicle = vehicles.get(0);
        if (vehicle.getSimNo() == null || vehicle.getSimNo().isBlank()) {
            throw new CmBusinessException("该车辆未绑定终端 SIM 卡，无法查询轨迹");
        }
        return vehicle;
    }

    private void requireRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new CmBusinessException("开始时间与结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new CmBusinessException("结束时间必须晚于开始时间");
        }
        if (Duration.between(startTime, endTime).compareTo(Duration.ofDays(QueryLimits.MAX_TRACK_RANGE_DAYS)) > 0) {
            throw new CmBusinessException("轨迹查询时间跨度不能超过 " + QueryLimits.MAX_TRACK_RANGE_DAYS + " 天");
        }
    }
}
