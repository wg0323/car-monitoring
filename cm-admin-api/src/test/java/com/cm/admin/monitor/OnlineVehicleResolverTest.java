package com.cm.admin.monitor;

import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.service.CmVehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineVehicleResolverTest {

    private static final long USER_ID = 100000000000120201L;

    private static final long DEPARTMENT_ID = 100000000117440544L;

    @Mock
    private MonitorPositionStore positionStore;

    @Mock
    private CmVehicleService vehicleService;

    private OnlineVehicleResolver resolver;

    private DataScope scope;

    @BeforeEach
    void setUp() {
        resolver = new OnlineVehicleResolver(positionStore, vehicleService);
        scope = new DataScope(USER_ID, false, List.of(1L), List.of(DEPARTMENT_ID));
    }

    @Test
    @DisplayName("不筛在线态时不读 Redis，也不查车辆表")
    void should_skip_when_online_is_null() {
        assertNull(resolver.resolve(scope, null));

        verifyNoInteractions(positionStore, vehicleService);
    }

    @Test
    @DisplayName("在线清单按数据范围求交后直接用于下推")
    void should_intersect_online_sims_with_scope() {
        Set<String> onlineSims = Set.of("sim-a", "sim-b");
        when(positionStore.onlineSimNos()).thenReturn(onlineSims);
        when(vehicleService.onlineSimNosInScope(scope, onlineSims)).thenReturn(List.of("sim-a"));

        assertEquals(List.of("sim-a"), resolver.resolve(scope, true));
    }

    @Test
    @DisplayName("在线车辆超过 1000 台时拒绝，不拼超长 IN 语句")
    void should_reject_oversized_online_set() {
        List<String> oversized = IntStream.range(0, 1001).mapToObj(index -> "sim-" + index).toList();
        Set<String> onlineSims = Set.copyOf(oversized);
        when(positionStore.onlineSimNos()).thenReturn(onlineSims);
        when(vehicleService.onlineSimNosInScope(scope, onlineSims)).thenReturn(oversized);

        assertThrows(CmBusinessException.class, () -> resolver.resolve(scope, false));
    }
}
