package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.row.VehicleDetailRow;
import com.cm.infrastructure.persistence.row.VehicleRow;

import java.util.Collection;
import java.util.List;

/**
 * 车辆基础资料查询。分页与车辆树都走同一份服务端数据范围。
 */
public interface CmVehicleService extends IService<CmVehicle> {

    IPage<VehicleRow> pageByScope(DataScope scope, long pageNo, long pageSize, String plateNo, String simNo);

    /**
     * 叠加部门等值筛选。departmentId 为 null 时与上一个重载等价；非空时调用方必须已校验其落在数据范围内。
     */
    IPage<VehicleRow> pageByScope(DataScope scope, long pageNo, long pageSize, String plateNo, String simNo,
                                 Long departmentId);

    /**
     * 在部门筛选之上叠加在线/离线筛选。online 为 null 表示不筛；
     * onlineSimNos 必须是「在线租约交集数据范围」后的清单，调用方先校验不超过 1000 条。
     */
    IPage<VehicleRow> pageByScope(DataScope scope, long pageNo, long pageSize, String plateNo, String simNo,
                                 Long departmentId, Boolean online, Collection<String> onlineSimNos);

    /**
     * 单车详情，数据范围与列表同一口径；越界或已删返回 null。
     */
    VehicleDetailRow detailInScope(DataScope scope, long id);

    /**
     * 把在线 SIM 集合与数据范围求交，按 1000 条分批。返回值即可直接用于在线/离线筛选。
     */
    List<String> onlineSimNosInScope(DataScope scope, Collection<String> onlineSimNos);

    List<VehicleRow> treeByScope(DataScope scope, String plateNo);

    /**
     * 范围内车辆总数，与列表 total 同一份计数口径。
     */
    long countByScope(DataScope scope);

    /**
     * 按 SIM 集合求与数据范围的交集；调用方负责按 1000 条分批。
     */
    List<VehicleRow> listInScopeBySimNos(DataScope scope, Collection<String> simNos);

    /**
     * 按车辆 ID 集合求交集，只用于订阅校验这类小集合场景。
     */
    List<VehicleRow> listInScopeByIds(DataScope scope, Collection<Long> ids);
}
