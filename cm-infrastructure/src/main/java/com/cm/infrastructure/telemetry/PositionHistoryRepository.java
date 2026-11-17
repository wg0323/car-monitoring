package com.cm.infrastructure.telemetry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.timeseries.Granularity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 历史轨迹写入。集合为时间序列：按 deviceTime 分桶、meta 存稳定维度、保存期到点自动清理。
 * 时间序列集合不支持唯一索引，重复投递由消费端去重键挡住，这里不做二次判重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionHistoryRepository {

    private static final String TIME_FIELD = "deviceTime";

    private static final String META_FIELD = "meta";

    private static final String META_SIM_FIELD = META_FIELD + ".simNo";

    private final MongoTemplate mongoTemplate;

    private final TelemetryProperties properties;

    @PostConstruct
    public void init() {
        if (!properties.getMongo().isAutoInit()) {
            return;
        }
        ensureCollection();
    }

    public void insert(PositionEvent event, VehicleBinding binding) {
        mongoTemplate.insert(toDocument(event, binding), properties.getMongo().getHistoryCollection());
    }

    /**
     * 建集合与索引可以重复执行：并发启动时若另一实例先建好，复查存在即视为成功；
     * 已存在时还要校验它确实是时间序列，否则保存期与分桶会静默失效。
     */
    void ensureCollection() {
        String collection = properties.getMongo().getHistoryCollection();
        if (!mongoTemplate.collectionExists(collection)) {
            try {
                mongoTemplate.createCollection(collection, CollectionOptions.timeSeries(TIME_FIELD,
                        series -> series.metaField(META_FIELD)
                                .granularity(Granularity.SECONDS)
                                .expireAfter(Duration.ofDays(properties.getMongo().getRetentionDays()))));
            } catch (RuntimeException ex) {
                if (!mongoTemplate.collectionExists(collection)) {
                    throw ex;
                }
                log.debug("时间序列集合已由其他实例创建, collection={}", collection);
            }
        } else {
            assertTimeSeries(collection);
        }
        mongoTemplate.indexOps(collection).ensureIndex(new Index().on(META_SIM_FIELD, Sort.Direction.ASC)
                .on(TIME_FIELD, Sort.Direction.ASC));
    }

    /**
     * 同名普通集合会让 expireAfter 与 seconds 分桶静默失效，必须显式失败而不是继续写入。
     */
    private void assertTimeSeries(String collection) {
        Document result = mongoTemplate.executeCommand(new Document("listCollections", 1)
                .append("filter", new Document("name", collection)));
        List<Document> batch = result.get("cursor", Document.class).getList("firstBatch", Document.class);
        Document options = batch.isEmpty() ? null : batch.get(0).get("options", Document.class);
        Document timeSeries = options == null ? null : options.get("timeseries", Document.class);
        if (timeSeries == null || !TIME_FIELD.equals(timeSeries.getString("timeField"))) {
            throw new IllegalStateException("集合 " + collection + " 已存在但不是按 " + TIME_FIELD
                    + " 建的时间序列集合，保存期与分桶都会失效");
        }
    }

    private Document toDocument(PositionEvent event, VehicleBinding binding) {
        Document meta = new Document("simNo", event.simNo())
                .append("vehicleId", binding.vehicleId())
                .append("companyId", binding.companyId());
        PositionSample sample = event.sample();
        return new Document("_id", event.eventId())
                .append(TIME_FIELD, toDate(event.deviceTime()))
                .append(META_FIELD, meta)
                .append("eventId", event.eventId())
                .append("eventType", event.eventType())
                .append("protocolBranch", event.protocolBranch())
                .append("simNo", event.simNo())
                .append("deviceSeq", event.deviceSeq())
                .append("ingestTime", toDate(event.ingestTime()))
                .append("effectiveDeviceTime", toDate(event.effectiveDeviceTime()))
                .append("timeQuality", event.timeQuality())
                .append("gatewayId", event.gatewayId())
                .append("traceId", event.traceId())
                .append("batchIndex", event.batchIndex())
                .append("alarmFlag", sample.alarmFlag())
                .append("status", sample.status())
                .append("latitude", sample.latitude())
                .append("longitude", sample.longitude())
                .append("altitude", sample.altitude())
                .append("speed", sample.speed())
                .append("course", sample.course())
                .append("mileage", sample.mileage())
                .append("oil", sample.oil())
                .append("recorderSpeed", sample.recorderSpeed())
                .append("signal", sample.signal())
                .append("voltage", sample.voltage())
                .append("payloadDigest", event.payloadDigest());
    }

    private Date toDate(String isoInstant) {
        return Date.from(Instant.parse(isoInstant));
    }
}
