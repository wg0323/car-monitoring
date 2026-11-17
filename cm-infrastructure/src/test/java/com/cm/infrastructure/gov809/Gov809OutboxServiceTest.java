package com.cm.infrastructure.gov809;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cm.infrastructure.persistence.entity.CmGov809Outbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 真实 MySQL 上的积压队列：按序号补发、重复事件只入队一次、失败可重试、序号可续。
 * 默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = Gov809TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id",
        "cm.gov809.outbox.max-attempt=2"
})
class Gov809OutboxServiceTest {

    private static final long PLATFORM_ID = 990801L;

    private static final String EVENT_PREFIX = "P3S3-OUTBOX-";

    private static final int POSITION_MSG_TYPE = 0x1200;

    private static final int SUB_TYPE_REAL_LOCATION = 0x1202;

    @Autowired
    private Gov809OutboxService outboxService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM cm_gov809_outbox WHERE platform_id = ?", PLATFORM_ID);
    }

    @Test
    @DisplayName("按入队顺序补发，成功后不再出现在待发列表")
    void should_replay_in_sequence_order() {
        outboxService.append(position(EVENT_PREFIX + "A"));
        outboxService.append(position(EVENT_PREFIX + "B"));
        outboxService.append(position(EVENT_PREFIX + "C"));

        List<CmGov809Outbox> pending = outboxService.claimPending(PLATFORM_ID, 10);
        assertEquals(3, pending.size());
        assertEquals(EVENT_PREFIX + "A", pending.get(0).getBizEventId());
        assertEquals(EVENT_PREFIX + "C", pending.get(2).getBizEventId());
        assertTrue(pending.get(0).getEventSeq() < pending.get(2).getEventSeq());

        outboxService.markSent(List.of(pending.get(0).getId()));

        assertEquals(2, outboxService.countPending(PLATFORM_ID));
        assertEquals(2, outboxService.claimPending(PLATFORM_ID, 10).size());
        assertEquals(pending.get(2).getEventSeq(), outboxService.maxAllocatedSeq(PLATFORM_ID));
    }

    @Test
    @DisplayName("同一业务事件重复入队只算一次")
    void should_ignore_duplicate_business_event() {
        assertTrue(outboxService.append(position(EVENT_PREFIX + "DUP")));
        assertFalse(outboxService.append(position(EVENT_PREFIX + "DUP")));

        assertEquals(1, outboxService.countPending(PLATFORM_ID));
    }

    @Test
    @DisplayName("发送失败重试到上限才置 FAILED")
    void should_retry_until_max_attempt() {
        outboxService.append(position(EVENT_PREFIX + "FAIL"));
        CmGov809Outbox row = outboxService.claimPending(PLATFORM_ID, 1).get(0);

        outboxService.markFailed(row.getId(), "link down");
        assertEquals(1, outboxService.countPending(PLATFORM_ID));

        outboxService.markFailed(row.getId(), "link down again");
        assertEquals(0, outboxService.countPending(PLATFORM_ID));
    }

    private Gov809OutboxMessage position(String bizEventId) {
        return new Gov809OutboxMessage(PLATFORM_ID, bizEventId, POSITION_MSG_TYPE,
                SUB_TYPE_REAL_LOCATION, "测A00001", 2, "00");
    }
}
 
