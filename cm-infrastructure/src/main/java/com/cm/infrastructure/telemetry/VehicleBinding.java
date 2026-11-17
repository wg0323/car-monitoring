package com.cm.infrastructure.telemetry;

/**
 * 车辆绑定信息。未绑定或查询失败时降级为 0，不阻塞轨迹落库；
 * plateNo / plateColor 供报警、里程统计与 809 转发使用，Mongo 的 meta 仍只用前两个字段。
 */
public record VehicleBinding(long vehicleId, long companyId, String plateNo, int plateColor,
                             long departmentId) {

    private static final VehicleBinding EMPTY = new VehicleBinding(0L, 0L, "", 0, 0L);

    public static VehicleBinding empty() {
        return EMPTY;
    }
}
