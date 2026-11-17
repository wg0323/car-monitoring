package com.cm.infrastructure.telemetry;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIM 到车辆归属的解析。轨迹的 meta 里要有 vehicleId 与 companyId，
 * 但每写一条都查库不现实，这里按 SIM 本地缓存十分钟；查询失败按未绑定处理，
 * 宁可少一个统计维度，也不让一次 SQL 故障堵死落库链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleBindingResolver {

    private static final long CACHE_TTL_MILLIS = 600_000L;

    private static final int MAX_CACHE_ENTRIES = 100_000;

    private final CmVehicleMapper vehicleMapper;

    private final Map<String, CachedBinding> cache = new ConcurrentHashMap<>();

    public VehicleBinding resolve(String simNo) {
        long now = System.currentTimeMillis();
        CachedBinding cached = cache.get(simNo);
        if (cached != null && cached.expireAt() > now) {
            return cached.binding();
        }
        VehicleBinding binding = query(simNo);
        if (binding == null) {
            return VehicleBinding.empty();
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(simNo, new CachedBinding(binding, now + CACHE_TTL_MILLIS));
        return binding;
    }

    /**
     * 查询失败返回 null，而不是空绑定：失败结论一旦进缓存，该 SIM 未来十分钟的轨迹
     * 会全部挂到未绑定的 meta 上，而时间序列的 meta 写入后无法就地修正。
     */
    private VehicleBinding query(String simNo) {
        try {
            CmVehicle vehicle = vehicleMapper.selectOne(Wrappers.<CmVehicle>lambdaQuery()
                    .eq(CmVehicle::getSimNo, simNo)
                    .last("LIMIT 1"));
            if (vehicle == null) {
                return VehicleBinding.empty();
            }
            return new VehicleBinding(vehicle.getId() == null ? 0L : vehicle.getId(),
                    vehicle.getCompanyId() == null ? 0L : vehicle.getCompanyId(),
                    vehicle.getPlateNo() == null ? "" : vehicle.getPlateNo(),
                    vehicle.getPlateColor() == null ? 0 : vehicle.getPlateColor(),
                    vehicle.getDepartmentId() == null ? 0L : vehicle.getDepartmentId());
        } catch (RuntimeException ex) {
            log.warn("车辆绑定查询失败，本次按未绑定处理且不缓存, simNo={}", simNo, ex);
            return null;
        }
    }

    private record CachedBinding(VehicleBinding binding, long expireAt) {
    }
}
