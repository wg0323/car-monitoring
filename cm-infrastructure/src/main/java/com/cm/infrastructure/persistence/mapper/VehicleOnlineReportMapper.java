package com.cm.infrastructure.persistence.mapper;

import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.row.VehicleOnlineReportRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 当前在线报表的车辆候选查询。只负责 MySQL 侧筛选，在线状态由调用方用 Redis 补齐。
 */
public interface VehicleOnlineReportMapper {

    List<VehicleOnlineReportRow> selectOnlineVehicleCandidates(@Param("scope") DataScope scope,
                                                               @Param("plateNo") String plateNo,
                                                               @Param("simNo") String simNo,
                                                               @Param("departmentId") Long departmentId);
}
