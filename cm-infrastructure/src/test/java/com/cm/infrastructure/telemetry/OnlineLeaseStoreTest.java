package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在线租约：注册带 TTL，续租与删除都要求连接代号匹配，扫描只返回本实例名下的 SIM。
 * 默认不跑，设置 CM_P3_REDIS_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_REDIS_TEST", matches = "true")
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.data.redis.host=${CM_P3_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${CM_P3_REDIS_PORT:6379}",
        "spring.data.redis.password=${CM_P3_REDIS_PASSWORD:}",
        "cm.telemetry.online.key-prefix=cm:test:online:",
        "cm.telemetry.online.lease-ttl-seconds=60"
})
@Import(OnlineLeaseStore.class)
class OnlineLeaseStoreTest {

    private static final String GATEWAY_A = "gw-test-a";

    private static final String GATEWAY_B = "gw-test-b";

    private static final String SIM_A = "13800138001";

    private static final String SIM_B = "13800138002";

    private static final long INCARNATION = 1001L;

    private static final long NEXT_INCARNATION = 1002L;

    @Autowired
    private OnlineLeaseStore leaseStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(List.of("cm:test:online:" + SIM_A, "cm:test:online:" + SIM_B));
    }

    @Test
    @DisplayName("注册后可读回，且只认本实例与连接代号")
    void should_register_and_read_back() {
        leaseStore.register(SIM_A, GATEWAY_A, INCARNATION);
        assertTrue(leaseStore.find(SIM_A).isPresent());
        assertEquals(GATEWAY_A, leaseStore.find(SIM_A).orElseThrow().gatewayId());
        assertEquals(INCARNATION, leaseStore.find(SIM_A).orElseThrow().incarnation());
    }

    @Test
    @DisplayName("旧连接代号续租失败，新代号续租成功")
    void should_renew_only_matching_incarnation() {
        leaseStore.register(SIM_A, GATEWAY_A, INCARNATION);
        assertFalse(leaseStore.renew(SIM_A, GATEWAY_A, NEXT_INCARNATION), "旧连接不能续新租约");
        assertTrue(leaseStore.renew(SIM_A, GATEWAY_A, INCARNATION));
    }

    @Test
    @DisplayName("旧连接代号删除失败，新代号删除成功")
    void should_remove_only_matching_incarnation() {
        leaseStore.register(SIM_A, GATEWAY_A, INCARNATION);
        assertFalse(leaseStore.removeIfMatch(SIM_A, GATEWAY_A, NEXT_INCARNATION));
        assertTrue(leaseStore.removeIfMatch(SIM_A, GATEWAY_A, INCARNATION));
        assertTrue(leaseStore.find(SIM_A).isEmpty());
    }

    @Test
    @DisplayName("已有新连接持有时旧连接补登记失败，不会覆盖新租约")
    void should_not_override_existing_lease() {
        leaseStore.register(SIM_A, GATEWAY_A, NEXT_INCARNATION);
        assertFalse(leaseStore.registerIfAbsent(SIM_A, GATEWAY_A, INCARNATION),
                "旧连接不能抢先补登记");
        assertEquals(NEXT_INCARNATION, leaseStore.find(SIM_A).orElseThrow().incarnation());
    }

    @Test
    @DisplayName("扫描只返回本网关实例名下的 SIM")
    void should_scan_only_own_gateway() {
        leaseStore.register(SIM_A, GATEWAY_A, INCARNATION);
        leaseStore.register(SIM_B, GATEWAY_B, INCARNATION);
        Set<String> own = Set.copyOf(leaseStore.scanByGateway(GATEWAY_A));
        assertEquals(Set.of(SIM_A), own);
    }
}
