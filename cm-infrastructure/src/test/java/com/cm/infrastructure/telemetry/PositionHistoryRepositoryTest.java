package com.cm.infrastructure.telemetry;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MongoDB 时间序列集合：参数、索引、写入字段与「重复事件仍然写两条」的行为。
 * 最后一条用实测结果锁定 P0 结论：时间序列集合没有唯一约束，去重只能靠消费端。
 * 默认不跑，设置 CM_P3_MONGO_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_MONGO_TEST", matches = "true")
@SpringBootTest(classes = P3TelemetryTestApplication.class, properties = {
        "spring.mongodb.uri=${CM_P3_MONGO_URI:mongodb://127.0.0.1:27017/cm_p3_telemetry_test}",
        "cm.telemetry.mongo.history-collection=position_history_test",
        "cm.telemetry.mongo.quarantine-collection=position_quarantine_test",
        "cm.telemetry.mongo.retention-days=90",
        "cm.telemetry.mongo.quarantine-retention-days=7",
        "cm.telemetry.mongo.auto-init=true"
})
@Import({PositionHistoryRepository.class, PositionQuarantineRepository.class})
class PositionHistoryRepositoryTest {

    private static final String COLLECTION = "position_history_test";

    private static final String QUARANTINE = "position_quarantine_test";

    private static final long RETENTION_SECONDS = 7776000L;

    private static final Instant DEVICE_TIME = Instant.parse("2026-11-15T04:00:00Z");

    private static final Instant INGEST_TIME = Instant.parse("2026-11-15T04:00:02Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PositionHistoryRepository historyRepository;

    @Autowired
    private PositionQuarantineRepository quarantineRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.dropCollection(QUARANTINE);
        historyRepository.ensureCollection();
        quarantineRepository.ensureCollection();
    }

    @Test
    @DisplayName("集合按 deviceTime 与 meta 建时间序列，保存期 90 天")
    void should_create_timeseries_collection_with_expected_options() {
        Document options = collectionOptions(COLLECTION);
        Document timeSeries = options.get("timeseries", Document.class);
        assertEquals("deviceTime", timeSeries.getString("timeField"));
        assertEquals("meta", timeSeries.getString("metaField"));
        assertEquals("seconds", timeSeries.getString("granularity"));
        long expireAfter = ((Number) options.get("expireAfterSeconds")).longValue();
        assertEquals(RETENTION_SECONDS, expireAfter);
    }

    @Test
    @DisplayName("按 SIM 与设备时间的二级索引已建立")
    void should_have_sim_time_index() {
        List<IndexInfo> indexes = mongoTemplate.indexOps(COLLECTION).getIndexInfo();
        boolean matched = indexes.stream()
                .flatMap(info -> info.getIndexFields().stream())
                .anyMatch(field -> "meta.simNo".equals(field.getKey()));
        assertTrue(matched, "缺 meta.simNo 索引，按车回放会退化为全桶扫描");
    }

    @Test
    @DisplayName("写入文档带 meta 三字段与设备时间")
    void should_write_document_with_meta() {
        historyRepository.insert(event("E1", DEVICE_TIME), new VehicleBinding(123L, 1L, "粤B12345", 1, 9L));
        Document saved = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("_id", "E1")).first();
        assertTrue(saved != null, "轨迹应已写入");
        Document meta = saved.get("meta", Document.class);
        assertEquals("13800138000", meta.getString("simNo"));
        assertEquals(123L, ((Number) meta.get("vehicleId")).longValue());
        assertEquals(1L, ((Number) meta.get("companyId")).longValue());
        assertEquals("NORMAL", saved.getString("timeQuality"));
    }

    @Test
    @DisplayName("同一事件写两次会落两条，时间序列集合不提供唯一约束")
    void should_keep_two_rows_when_same_event_inserted_twice() {
        historyRepository.insert(event("E1", DEVICE_TIME), VehicleBinding.empty());
        historyRepository.insert(event("E1", DEVICE_TIME), VehicleBinding.empty());
        long count = mongoTemplate.getCollection(COLLECTION)
                .countDocuments(new Document("eventId", "E1"));
        assertEquals(2L, count, "时间序列集合不会因 _id 相同而拒绝写入，去重必须由消费端完成");
    }

    @Test
    @DisplayName("隔离集合按接入时间建 TTL 索引")
    void should_create_quarantine_ttl_index() {
        List<IndexInfo> indexes = mongoTemplate.indexOps(QUARANTINE).getIndexInfo();
        boolean ttl = indexes.stream()
                .map(IndexInfo::getExpireAfter)
                .flatMap(Optional::stream)
                .anyMatch(duration -> duration.getSeconds() == 604800L);
        assertTrue(ttl, "隔离集合必须有 7 天 TTL，否则异常时间数据会长期堆积");
        quarantineRepository.insert(event("Q1", DEVICE_TIME.plusSeconds(86400 * 365)));
        assertEquals(1L, mongoTemplate.getCollection(QUARANTINE)
                .countDocuments(new Document("_id", "Q1")));
    }

    private Document collectionOptions(String collection) {
        Document result = mongoTemplate.executeCommand(new Document("listCollections", 1)
                .append("filter", new Document("name", collection)));
        Document cursor = result.get("cursor", Document.class);
        List<Document> batch = cursor.getList("firstBatch", Document.class);
        assertEquals(1, batch.size(), "集合应已创建: " + collection);
        return batch.get(0).get("options", Document.class);
    }

    private PositionEvent event(String eventId, Instant deviceTime) {
        PositionSample sample = new PositionSample(0, 1, 30000000, 120000000, 0, 60, 90, 100L, 0, 0, 0, 0);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, eventId, PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, "13800138000", 1, deviceTime.toString(),
                INGEST_TIME.toString(), deviceTime.toString(), "NORMAL", "gw-1", eventId, 0, sample,
                "abcdef0123456789");
    }
}
