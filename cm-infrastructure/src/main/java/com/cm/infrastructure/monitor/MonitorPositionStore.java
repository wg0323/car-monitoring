package com.cm.infrastructure.monitor;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.TelemetryJson;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 实时监控页的 Redis 读取口。位置与在线态都在 Redis，MySQL 只负责车辆与数据范围。
 *
 * <p>终端一律以 {@code cm_vehicle.sim_no} 为锚点：{@code cm_terminal.sim_no} 在 P4-1 已确认不可作为判据，
 * 这里也不碰终端表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorPositionStore {

    private static final String FIELD_DATA = "data";

    private static final byte[] FIELD_DATA_BYTES = FIELD_DATA.getBytes(StandardCharsets.UTF_8);

    private static final int SCAN_BATCH = 1000;

    private final StringRedisTemplate redisTemplate;

    private final TelemetryProperties properties;

    public Optional<PositionSnapshot> latest(String simNo) {
        Object data = redisTemplate.opsForHash().get(latestKey(simNo), FIELD_DATA);
        return data == null ? Optional.empty() : parse(data.toString());
    }

    /**
     * 批量取最新位置。逐条 HGET 在万级在线量下会把往返次数放大到不可接受，
     * 这里用管道一次发完，解析失败的单条跳过而不是让整批失败。
     */
    public Map<String, PositionSnapshot> latestAll(Collection<String> simNos) {
        if (simNos.isEmpty()) {
            // 用 emptyMap 而不是 Map.of()：Map.of() 是不可变的，get(null) 会抛空指针。
            return Collections.emptyMap();
        }
        List<String> ordered = List.copyOf(simNos);
        List<Object> raw = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String simNo : ordered) {
                connection.hashCommands().hGet(latestKey(simNo).getBytes(StandardCharsets.UTF_8),
                        FIELD_DATA_BYTES);
            }
            return null;
        });
        Map<String, PositionSnapshot> snapshots = new HashMap<>(ordered.size());
        for (int index = 0; index < ordered.size() && index < raw.size(); index++) {
            String simNo = ordered.get(index);
            String json = asText(raw.get(index));
            if (json == null) {
                continue;
            }
            parse(json).ifPresent(snapshot -> snapshots.put(simNo, snapshot));
        }
        return snapshots;
    }

    public boolean online(String simNo) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(onlineKey(simNo)));
    }

    /**
     * 批量判在线。列表一页最多上千行，逐条 EXISTS 的往返次数与行数成正比，这里同样走管道。
     */
    public Map<String, Boolean> onlineAll(Collection<String> simNos) {
        if (simNos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> ordered = List.copyOf(simNos);
        List<Object> raw = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String simNo : ordered) {
                connection.keyCommands().exists(onlineKey(simNo).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        Map<String, Boolean> states = new HashMap<>(ordered.size());
        for (int index = 0; index < ordered.size() && index < raw.size(); index++) {
            states.put(ordered.get(index), Boolean.TRUE.equals(raw.get(index)));
        }
        return states;
    }

    /**
     * 当前所有在线终端。租约键由网关按心跳续期，过期的键会被 Redis 自己删掉，
     * 所以这里扫到的就是「此刻在线」的集合。
     */
    public Set<String> onlineSimNos() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(properties.getOnline().getKeyPrefix() + "*")
                .count(SCAN_BATCH)
                .build();
        Set<String> simNos = new LinkedHashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                simNos.add(key.substring(properties.getOnline().getKeyPrefix().length()));
            }
        }
        return simNos;
    }

    /**
     * 停车数：在线且最新位置速度为零。速度取不到（租约在、位置过期）时不计入停车，宁可少报也不猜。
     */
    public int parkingCount(Collection<String> simNos) {
        return (int) latestAll(simNos).values().stream()
                .filter(snapshot -> snapshot.speed() == 0)
                .count();
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    private Optional<PositionSnapshot> parse(String json) {
        try {
            return Optional.of(PositionSnapshot.of(TelemetryJson.read(json, PositionEvent.class)));
        } catch (RuntimeException ex) {
            log.warn("最新位置解析失败，按无位置处理", ex);
            return Optional.empty();
        }
    }

    private String latestKey(String simNo) {
        return properties.getLatest().getKeyPrefix() + simNo;
    }

    private String onlineKey(String simNo) {
        return properties.getOnline().getKeyPrefix() + simNo;
    }
}
