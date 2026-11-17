package com.cm.infrastructure.telemetry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * 异常时间隔离。FUTURE 与 STALE 数据不入主集合，避免污染时间序列分桶与 TTL，
 * 但原始事件仍保留在这里供排查；集合按接入时间过期，自身不会长期堆积。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionQuarantineRepository {

    private static final String INGEST_FIELD = "ingestTime";

    private static final long SECONDS_PER_DAY = 86400L;

    private final MongoTemplate mongoTemplate;

    private final TelemetryProperties properties;

    @PostConstruct
    public void init() {
        if (!properties.getMongo().isAutoInit()) {
            return;
        }
        ensureCollection();
    }

    public void insert(PositionEvent event) {
        PositionSample sample = event.sample();
        Document document = new Document("_id", event.eventId())
                .append("eventId", event.eventId())
                .append("simNo", event.simNo())
                .append("deviceSeq", event.deviceSeq())
                .append("deviceTime", Date.from(Instant.parse(event.deviceTime())))
                .append(INGEST_FIELD, Date.from(Instant.parse(event.ingestTime())))
                .append("timeQuality", event.timeQuality())
                .append("protocolBranch", event.protocolBranch())
                .append("gatewayId", event.gatewayId())
                .append("traceId", event.traceId())
                .append("batchIndex", event.batchIndex())
                .append("latitude", sample.latitude())
                .append("longitude", sample.longitude())
                .append("speed", sample.speed())
                .append("course", sample.course())
                .append("payloadDigest", event.payloadDigest());
        try {
            mongoTemplate.insert(document, properties.getMongo().getQuarantineCollection());
        } catch (DuplicateKeyException ex) {
            // 门闩已过 TTL 后死信重放会再次走到这里，视为已隔离而不是再次入死信。
            log.debug("隔离事件已存在，跳过重复写入, eventId={}", event.eventId());
        }
    }

    void ensureCollection() {
        String collection = properties.getMongo().getQuarantineCollection();
        if (!mongoTemplate.collectionExists(collection)) {
            try {
                mongoTemplate.createCollection(collection);
            } catch (RuntimeException ex) {
                if (!mongoTemplate.collectionExists(collection)) {
                    throw ex;
                }
                log.debug("隔离集合已由其他实例创建, collection={}", collection);
            }
        }
        mongoTemplate.indexOps(collection).ensureIndex(new Index().on(INGEST_FIELD, Sort.Direction.ASC)
                .expire(properties.getMongo().getQuarantineRetentionDays() * SECONDS_PER_DAY));
    }
}
