package com.cm.infrastructure.telemetry;

import com.cm.domain.track.TrackQuery;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 历史轨迹只读查询。时间序列集合只允许按时间字段排序，
 * 同一秒内的多个点顺序由存储层决定；列表与导出走同一条件与同一排序，两者行集必然一致。
 */
@Component
@RequiredArgsConstructor
public class PositionTrackRepository {

    private static final String TIME_FIELD = "deviceTime";

    private static final String META_SIM_FIELD = "meta.simNo";

    private static final String META_VEHICLE_FIELD = "meta.vehicleId";

    private final MongoTemplate mongoTemplate;

    private final TelemetryProperties properties;

    public long count(TrackQuery query) {
        return mongoTemplate.count(new Query(criteria(query)), collection());
    }

    /**
     * 按设备时间升序取点。maxRows 由调用方先用 count 校验，这里只做兜底截断。
     */
    public List<TrackPoint> find(TrackQuery query, long maxRows) {
        Query mongoQuery = new Query(criteria(query))
                .with(Sort.by(Sort.Order.asc(TIME_FIELD)))
                .limit((int) maxRows);
        return mongoTemplate.find(mongoQuery, Document.class, collection())
                .stream()
                .map(this::toTrackPoint)
                .toList();
    }

    private Criteria criteria(TrackQuery query) {
        return Criteria.where(META_SIM_FIELD).is(query.simNo())
                .and(META_VEHICLE_FIELD).is(query.vehicleId())
                .and(TIME_FIELD).gte(toDate(query.startTime())).lte(toDate(query.endTime()));
    }

    private TrackPoint toTrackPoint(Document document) {
        return new TrackPoint(
                document.getString("_id"),
                document.getDate(TIME_FIELD).toInstant(),
                intValue(document, "latitude"),
                intValue(document, "longitude"),
                intValue(document, "altitude"),
                intValue(document, "speed"),
                intValue(document, "recorderSpeed"),
                intValue(document, "course"),
                longValue(document, "mileage"),
                intValue(document, "oil"),
                intValue(document, "status"),
                intValue(document, "alarmFlag"));
    }

    private int intValue(Document document, String field) {
        Number value = document.get(field, Number.class);
        return value == null ? 0 : value.intValue();
    }

    private long longValue(Document document, String field) {
        Number value = document.get(field, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(zone()).toInstant());
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTime().getZone());
    }

    private String collection() {
        return properties.getMongo().getHistoryCollection();
    }
}
