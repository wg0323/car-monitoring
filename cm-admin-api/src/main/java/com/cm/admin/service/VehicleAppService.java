package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.VehicleDetailVO;
import com.cm.admin.vo.VehicleVO;

/**
 * 车辆用例。数据范围由服务端按登录主体构造，部门筛选值必须落在范围内。
 */
public interface VehicleAppService {

    /**
     * 车辆列表。online 为 null 表示不筛，true 只查在线，false 只查离线。
     */
    PageResult<VehicleVO> page(Long userId, long pageNo, long pageSize, String plateNo, String simNo,
                               Long departmentId, Boolean online);

    /**
     * 车辆详情。越界与不存在都拒绝，不区分两种原因。
     */
    VehicleDetailVO detail(Long userId, Long vehicleId);
}
