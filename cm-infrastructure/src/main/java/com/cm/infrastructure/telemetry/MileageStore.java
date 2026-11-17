package com.cm.infrastructure.telemetry;

import com.cm.domain.mileage.MileageDeltaPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 里程累计状态。基线（该车上次累计值）与当日累计都放 Redis：
 * 定位主链路不写 MySQL，入库交给定时 flush 的当日绝对值覆盖。
 *
 * <p>脚本带 CAS 守卫：只有库里的基线仍是上次读到的那一个值时才落盘。
 * 并发消费或重复投递时守卫失败，本次样本跳过；基线不推进，下一个样本会把这段增量补齐，不会多算也不会少算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MileageStore {

    private static final String FIELD_BASE_RAW = "raw";

    private static final String FIELD_TOTAL = "total";

    private static final String FIELD_FIRST = "first";

    private static final String FIELD_LAST = "last";

    private static final String FIELD_VEHICLE_ID = "vehicleId";

    private static final String FIELD_PLATE_NO = "plateNo";

    private static final String FIELD_DEPARTMENT_ID = "departmentId";

    private static final String FIELD_COMPANY_ID = "companyId";

    private static final String FIELD_FLUSHED = "flushed";

    private static final String NO_BASE = "0";

    private static final String SCRIPT_RESULT_CONFLICT = "-1";

    private static final int SCAN_BATCH = 500;

    private static final long SECONDS_PER_DAY = 86_400L;

    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * KEYS[1]=基线，KEYS[2]=当日记账；ARGV 依次为：期望基线、当前累计、本次增量、当前累计公里、TTL、车辆四元信息。
     */
    private static final DefaultRedisScript<Long> ACCUMULATE = new DefaultRedisScript<>(
            "local last = redis.call('HGET', KEYS[1], 'raw') "
                    + "local expected = ARGV[1] "
                    + "if expected == '0' then "
                    + "  if last then return -1 end "
                    + "elseif last ~= expected then return -1 end "
                    + "redis.call('HSET', KEYS[1], 'raw', ARGV[2]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[5]) "
                    + "if tonumber(ARGV[3]) > 0 then redis.call('HINCRBYFLOAT', KEYS[2], 'total', ARGV[3]) end "
                    + "if redis.call('HEXISTS', KEYS[2], 'first') == 0 then "
                    + "  redis.call('HSET', KEYS[2], 'first', ARGV[4]) end "
                    + "redis.call('HSET', KEYS[2], 'last', ARGV[4]) "
                    // 车牌等归属信息只在拿到非空值时写入：绑定查询失败或车辆解绑会传空串，
                    // 无条件覆盖会让当日记账失去车牌，这一天的里程就再也落不了库。
                    + "if ARGV[7] ~= '' then "
                    + "  redis.call('HSET', KEYS[2], 'vehicleId', ARGV[6], 'plateNo', ARGV[7], "
                    + "    'departmentId', ARGV[8], 'companyId', ARGV[9]) end "
                    + "redis.call('PEXPIRE', KEYS[2], ARGV[5]) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    private final TelemetryProperties properties;

    private final TelemetryMetrics metrics;

    /**
     * @return true 表示本次增量已计入当日累计
     */
    public boolean accumulate(PositionEvent event, VehicleBinding binding) {
        PositionSample sample = event.sample();
        if (sample == null || sample.mileage() <= 0L) {
            metrics.mileageDiscarded(MileageDeltaPolicy.Status.MISSING.name());
            return false;
        }
        return accumulateFrom(event, binding, baseRaw(properties.getMileage().getBaseKeyPrefix() + event.simNo()));
    }

    /**
     * 用「调用方读到的基线」做 CAS。正常链路只经 accumulate 走这里；
     * 单独拉出来是为了能直接验证守卫分支。
     */
    boolean accumulateFrom(PositionEvent event, VehicleBinding binding, String expectedBase) {
        PositionSample sample = event.sample();
        String baseKey = properties.getMileage().getBaseKeyPrefix() + event.simNo();
        String dayKey = dayKey(event.simNo(), dayOf(event));
        MileageDeltaPolicy.Delta delta = MileageDeltaPolicy.evaluate(
                parseLong(expectedBase), sample.mileage(), maxDeltaRaw());
        Long applied = redisTemplate.execute(ACCUMULATE, List.of(baseKey, dayKey),
                expectedBase, String.valueOf(sample.mileage()),
                kilometers(delta.deltaRaw()).toPlainString(),
                kilometers(sample.mileage()).toPlainString(),
                String.valueOf(ttlMillis()),
                String.valueOf(binding.vehicleId()), binding.plateNo(),
                String.valueOf(binding.departmentId()), String.valueOf(binding.companyId()));
        if (applied == null || SCRIPT_RESULT_CONFLICT.equals(String.valueOf(applied))) {
            metrics.mileageDiscarded("CONFLICT");
            return false;
        }
        if (delta.counted()) {
            metrics.mileageCounted();
            return true;
        }
        metrics.mileageDiscarded(delta.status().name());
        return false;
    }

    public List<String> dayKeys() {
        List<String> keys = new ArrayList<>();
        try (var cursor = redisTemplate.scan(ScanOptions.scanOptions()
                .match(properties.getMileage().getDayKeyPrefix() + "*")
                .count(SCAN_BATCH)
                .build())) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    public MileageDay readDay(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }
        return new MileageDay(key,
                text(entries, FIELD_PLATE_NO),
                parseLong(text(entries, FIELD_VEHICLE_ID)),
                parseLong(text(entries, FIELD_DEPARTMENT_ID)),
                parseLong(text(entries, FIELD_COMPANY_ID)),
                decimal(text(entries, FIELD_TOTAL)),
                decimal(text(entries, FIELD_FIRST)),
                decimal(text(entries, FIELD_LAST)),
                decimalOrNull(text(entries, FIELD_FLUSHED)));
    }

    /**
     * 只记录已落库的当日累计：下次 flush 看到两者相等就跳过，不会把没变化的车反复写库。
     */
    public void markFlushed(String key, BigDecimal total) {
        redisTemplate.opsForHash().put(key, FIELD_FLUSHED, total.toPlainString());
    }

    private String dayOf(PositionEvent event) {
        ZoneId zone = ZoneId.of(properties.getTime().getZone());
        return LocalDate.ofInstant(Instant.parse(event.deviceTime()), zone).toString().replace("-", "");
    }

    private String dayKey(String simNo, String day) {
        return properties.getMileage().getDayKeyPrefix() + simNo + ":" + day;
    }

    private String baseRaw(String baseKey) {
        Object raw = redisTemplate.opsForHash().get(baseKey, FIELD_BASE_RAW);
        return raw == null ? NO_BASE : raw.toString();
    }

    private long maxDeltaRaw() {
        return properties.getMileage().getMaxDeltaKilometers() * MileageDeltaPolicy.RAW_PER_KILOMETER;
    }

    private long ttlMillis() {
        return properties.getMileage().getRetentionDays() * SECONDS_PER_DAY * MILLIS_PER_SECOND;
    }

    private static BigDecimal kilometers(long raw) {
        return BigDecimal.valueOf(raw).movePointLeft(1);
    }

    private static String text(Map<Object, Object> entries, String field) {
        Object value = entries.get(field);
        return value == null ? "" : value.toString();
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    /**
     * flushed 为 null 表示从未落库；与 total 为 0 的情况必须区分，否则零里程的一天永远不会写入。
     */
    private static BigDecimal decimalOrNull(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public record MileageDay(String key, String plateNo, long vehicleId, long departmentId,
                             long companyId, BigDecimal total, BigDecimal first,
                             BigDecimal last, BigDecimal flushed) {

        /**
         * 未绑定车辆的 plateNo 为空，写入会全部挤到同一行，直接跳过。
         */
        public boolean flushable() {
            return !plateNo.isBlank();
        }
    }
}
