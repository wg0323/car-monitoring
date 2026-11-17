package com.cm.admin.monitor;

import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.service.CmVehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在线/离线筛选的清单解析：先取 Redis 在线租约集合，再与数据范围求交。
 *
 * <p>求交后的清单会作为 SQL IN / NOT IN 的参数下推，必须遵守 1000 个元素上限；
 * 在线车辆超过上限时直接拒绝并提示缩小范围，而不是拼一条超长 SQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineVehicleResolver {

    private static final int MAX_FILTER_SIM_NOS = DataScope.MAX_SCOPE_IDS;

    private final MonitorPositionStore positionStore;

    private final CmVehicleService vehicleService;

    /**
     * @param online null 表示不筛；true 只查在线；false 只查离线
     * @return 可用于下推的在线 SIM 清单；online 为 null 时返回 null
     */
    public List<String> resolve(DataScope scope, Boolean online) {
        if (online == null) {
            return null;
        }
        List<String> simNos = vehicleService.onlineSimNosInScope(scope, positionStore.onlineSimNos());
        if (simNos.size() > MAX_FILTER_SIM_NOS) {
            throw new CmBusinessException("在线车辆超过 " + MAX_FILTER_SIM_NOS + " 台，请先按部门或车牌缩小范围");
        }
        log.debug("在线筛选清单解析完成, userId={}, online={}, size={}", scope.getUserId(), online, simNos.size());
        return simNos;
    }
}
