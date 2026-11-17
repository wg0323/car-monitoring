package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.P3TestApplication;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.mapper.CmCommandOutboxMapper;
import com.cm.infrastructure.service.CmCommandOutboxLeaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 上的租约抢占：多实例并发抢同一批记录，同一记录只被抢到一次；
 * 租约到期后其他实例可以接手。默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = P3TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id",
        "cm.command.relay.lease-seconds=30",
        "cm.command.relay.batch-size=10"
})
class CommandOutboxLeaseConcurrencyTest {

    private static final String PREFIX = "P3-LEASE-";

    private static final int ROW_COUNT = 200;

    private static final int THREAD_COUNT = 16;

    private static final int MAX_ATTEMPTS_PER_THREAD = 200;

    private static final long AWAIT_SECONDS = 60L;

    @Autowired
    private CmCommandOutboxLeaseService leaseService;

    @Autowired
    private CmCommandOutboxMapper outboxMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String runPrefix;

    @BeforeEach
    void setUp() {
        runPrefix = PREFIX + UUID.randomUUID();
        jdbcTemplate.update("DELETE FROM cm_command_outbox WHERE command_id LIKE ?", PREFIX + "%");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM cm_command_outbox WHERE command_id LIKE ?", PREFIX + "%");
    }

    @Test
    @DisplayName("两个实例十六个线程并发抢占，两百条记录每条只被抢到一次")
    void should_claim_each_row_exactly_once() throws Exception {
        insertRows(ROW_COUNT);
        List<Long> claimedIds = Collections.synchronizedList(new ArrayList<>());
        ThreadPoolExecutor pool = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < THREAD_COUNT; index++) {
                String owner = index % 2 == 0 ? "instance-a" : "instance-b";
                futures.add(pool.submit(() -> {
                    start.await();
                    drain(owner, claimedIds);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(ROW_COUNT, claimedIds.size(), "应恰好抢到全部记录");
        assertEquals(ROW_COUNT, new HashSet<>(claimedIds).size(), "同一记录不允许被两个实例同时抢到");
    }

    @Test
    @DisplayName("租约到期后由其他实例接手")
    void should_allow_takeover_after_lease_expires() {
        CmCommandOutbox row = insertRow(runPrefix + "-takeover");

        List<CmCommandOutbox> first = leaseService.claim("instance-a");
        assertEquals(1, first.size());
        assertEquals(row.getId(), first.get(0).getId());
        assertTrue(leaseService.claim("instance-b").isEmpty(), "租约未到期时另一个实例抢不到");

        // 直接把租约推到过期，避免用睡眠等待；只改本次测试自己插入的那一行。
        jdbcTemplate.update("UPDATE cm_command_outbox SET lease_expire_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND) "
                + "WHERE id = ?", row.getId());

        List<CmCommandOutbox> second = leaseService.claim("instance-b");
        assertEquals(1, second.size());
        assertEquals(row.getId(), second.get(0).getId());
    }

    private void drain(String owner, List<Long> claimedIds) throws InterruptedException {
        int attempts = 0;
        while (claimedIds.size() < ROW_COUNT && attempts < MAX_ATTEMPTS_PER_THREAD) {
            List<CmCommandOutbox> rows = leaseService.claim(owner);
            attempts++;
            for (CmCommandOutbox row : rows) {
                claimedIds.add(row.getId());
            }
            if (rows.isEmpty()) {
                Thread.sleep(5L);
            }
        }
    }

    private void insertRows(int count) {
        for (int index = 0; index < count; index++) {
            insertRow(runPrefix + "-" + index);
        }
    }

    private CmCommandOutbox insertRow(String commandId) {
        CmCommandOutbox row = new CmCommandOutbox();
        row.setCommandId(commandId);
        row.setExchangeName("cm.command");
        row.setRoutingKey("command.downlink.gw-1");
        row.setPayload(commandId);
        row.setPublishStatus("PENDING");
        row.setRetryCount(0);
        row.setDeleted(false);
        outboxMapper.insert(row);
        return row;
    }
}
