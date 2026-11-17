package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.MonitorSummaryVO;
import com.cm.admin.vo.MonitorVehicleVO;

import java.util.Collection;
import java.util.Map;

/**
 * 实时监控用例。位置与在线态来自 Redis，车辆与数据范围来自 MySQL；
 * 两条链路都只接受登录主体 ID 生成的范围，不接受请求自带的权限参数。
 */
public interface MonitorAppService {

    /**
     * 分页查询实时监控行。keyword 按车牌前缀匹配，departmentId 必须落在数据范围内；
     * online 为 null 表示不筛，true 只查在线，false 只查离线。
     */
    PageResult<MonitorVehicleVO> page(Long userId, long pageNo, long pageSize, String keyword, Long departmentId,
                                      Boolean online);

    /**
     * 状态栏统计。结果按账号缓存数秒，避免每次刷新都做全量 SCAN。
     */
    MonitorSummaryVO summary(Long userId);

    /**
     * 订阅校验：返回「请求车辆 ID 与数据范围的交集」，值是对应的 SIM 号。
     * 越界 ID 不出现在返回里，调用方必须据此丢弃，不得回退成全量。
     */
    Map<Long, String> vehiclesInScope(Long userId, Collection<Long> vehicleIds);
}
