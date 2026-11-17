package com.cm.infrastructure.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 消费链路端到端幂等：同一事件消费两次只落一条轨迹、最新位置只保留新版本，
 * 异常时间转隔离集合。默认不跑，同时设置 CM_P3_MONGO_TEST 与 CM_P3_REDIS_TEST 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_MONGO_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "CM_P3_REDIS_TEST", matches = "true")
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.data.redis.host=${CM_P3_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${CM_P3_REDIS_PORT:6379}",
        "spring.data.redis.password=${CM_P3_REDIS_PASSWORD:}",
        "spring.mongodb.uri=${CM_P3_MONGO_URI:mongodb://127.0.0.1:27017/cm_p3_telemetry_test}",
        "cm.telemetry.dedup.key-prefix=cm:test:dedup:",
        "cm.telemetry.dedup.ttl-seconds=300",
        "cm.telemetry.latest.key-prefix=cm:test:position:",
        "cm.telemetry.latest.ttl-seconds=300",
        "cm.telemetry.mongo.history-collection=position_history_test",
        "cm.telemetry.mongo.quarantine-collection=position_quarantine_test",
        "cm.telemetry.mongo.auto-init=true"
})
@Import({TelemetryHistoryHandler.class, TelemetryLatestHandler.class, PositionDedupStore.class,
        PositionLatestStore.class, PositionHistoryRepository.class, PositionQuarantineRepository.class,
        PositionBroadcaster.class, TelemetryMetrics.class})
class TelemetryConsumerIdempotencyTest {

    private static final String COLLECTION = "position_history_test";

    private static final String QUARANTINE = "position_quarantine_test";

    private static final String SIM_NO = "13800138000";

    private static final Instant DEVICE_TIME = Instant.parse("2026-11-15T04:00:00Z");

    private static final Instant INGEST_TIME = Instant.parse("2026-11-15T04:00:02Z");

    @MockitoBean
    private VehicleBindingResolver bindingResolver;

    @MockitoBean
    private MileageStore mileageStore;

    @Autowired
    private TelemetryHistoryHandler historyHandler;

    @Autowired
    private TelemetryLatestHandler latestHandler;

    @Autowired
    private PositionHistoryRepository historyRepository;

    @Autowired
    private PositionQuarantineRepository quarantineRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        when(bindingResolver.resolve(anyString())).thenReturn(new VehicleBinding(123L, 1L, "粤B12345", 1, 9L));
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(QUARANTINE);
        historyRepository.ensureCollection();
        quarantineRepository.ensureCollection();
        redisTemplate.delete("cm:test:position:" + SIM_NO);
        // 去重键 TTL 是分钟级，不清理的话连续跑两次就会因为「已处理过」而不落库。
        redisTemplate.delete(List.of(
                "cm:test:dedup:E1", "cm:test:dedup:E2", "cm:test:dedup:E3", "cm:test:dedup:E4"));
    }

    @Test
    @DisplayName("同一事件消费两次只落一条轨迹")
    void should_write_one_row_when_same_event_consumed_twice() {
        String payload = TelemetryJson.write(event("E1", DEVICE_TIME, INGEST_TIME, "NORMAL",
                DEVICE_TIME));
        historyHandler.handle(payload);
        historyHandler.handle(payload);
        assertEquals(1L, mongoTemplate.getCollection(COLLECTION)
                .countDocuments(new org.bson.Document("eventId", "E1")));
    }

    @Test
    @DisplayName("未来时间事件进入隔离集合，不写主集合")
    void should_route_future_event_to_quarantine() {
        Instant future = INGEST_TIME.plusSeconds(3600);
        String payload = TelemetryJson.write(event("E2", future, INGEST_TIME, "FUTURE", INGEST_TIME));
        historyHandler.handle(payload);
        assertEquals(0L, mongoTemplate.getCollection(COLLECTION)
                .countDocuments(new org.bson.Document("eventId", "E2")));
        assertEquals(1L, mongoTemplate.getCollection(QUARANTINE)
                .countDocuments(new org.bson.Document("eventId", "E2")));
    }

    @Test
    @DisplayName("迟到事件不会覆盖最新位置")
    void should_not_overwrite_latest_with_older_event() {
        latestHandler.handle(TelemetryJson.write(event("E3", DEVICE_TIME.plusSeconds(60),
                INGEST_TIME.plusSeconds(60), "NORMAL", DEVICE_TIME.plusSeconds(60))));
        latestHandler.handle(TelemetryJson.write(event("E4", DEVICE_TIME, INGEST_TIME, "NORMAL", DEVICE_TIME)));
        String latest = redisTemplate.opsForHash().get("cm:test:position:" + SIM_NO, "data").toString();
        assertTrue(latest.contains("\"eventId\":\"E3\""), "旧事件不应覆盖新版本");
    }

    private PositionEvent event(String eventId, Instant deviceTime, Instant ingestTime, String quality,
            Instant effectiveTime) {
        PositionSample sample = new PositionSample(0, 1, 30000000, 120000000, 0, 60, 90, 100L, 0, 0, 0, 0);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, eventId, PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, SIM_NO, 1, deviceTime.toString(), ingestTime.toString(),
                effectiveTime.toString(), quality, "gw-1", eventId, 0, sample, "abcdef0123456789");
    }
}
