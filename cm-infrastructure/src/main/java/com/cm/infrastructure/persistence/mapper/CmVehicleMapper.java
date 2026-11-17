package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.row.VehicleDetailRow;
import com.cm.infrastructure.persistence.row.VehicleRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 车辆表 Mapper。复杂关联与数据范围过滤写在 mapper/VehicleMapper.xml。
 */
public interface CmVehicleMapper extends BaseMapper<CmVehicle> {

    List<VehicleRow> selectVehiclePage(@Param("scope") DataScope scope,
                                       @Param("plateNo") String plateNo,
                                       @Param("simNo") String simNo,
                                       @Param("departmentId") Long departmentId,
                                       @Param("online") Boolean online,
                                       @Param("onlineSimNos") Collection<String> onlineSimNos,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    long countVehiclePage(@Param("scope") DataScope scope,
                          @Param("plateNo") String plateNo,
                          @Param("simNo") String simNo,
                          @Param("departmentId") Long departmentId,
                          @Param("online") Boolean online,
                          @Param("onlineSimNos") Collection<String> onlineSimNos);

    /**
     * 单车详情，数据范围与列表同一口径；越界或已删返回 null。
     */
    VehicleDetailRow selectVehicleDetail(@Param("scope") DataScope scope, @Param("id") long id);

    List<VehicleRow> selectVehicleTree(@Param("scope") DataScope scope,
                                       @Param("plateNo") String plateNo);

    /**
     * 按 SIM 集合求与数据范围的交集。调用方负责按 1000 条分批（IN 元素上限）。
     */
    List<VehicleRow> selectVehiclesInScopeBySimNos(@Param("scope") DataScope scope,
                                                   @Param("simNos") Collection<String> simNos);

    /**
     * 按车辆 ID 集合求交集，供 WebSocket 订阅校验使用。
     */
    List<VehicleRow> selectVehiclesInScopeByIds(@Param("scope") DataScope scope,
                                                @Param("ids") Collection<Long> ids);
}
