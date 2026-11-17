package com.cm.infrastructure.telemetry;

import com.cm.domain.position.TimeQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 最新位置消费。版本比较在 Redis 原子脚本里完成，乱序与迟到事件不会覆盖更新的位置；
 * 异常时间事件不参与最新位置，避免未来时间长期霸占。
 *
 * <p>里程累计挂在同一条链路且只在「确实写入最新位置」时累加：
 * 乱序、迟到、旧版本的事件不会把累计里程往回推。
 *
 * <p>写入成功后向 Redis 频道广播一条精简事件，供管理端实时监控页扇出；广播失败不影响落库与里程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryLatestHandler {

    private static final String FUTURE = TimeQuality.FUTURE.name();

    private static final String STALE = TimeQuality.STALE.name();

    private final PositionLatestStore latestStore;

    private final MileageStore mileageStore;

    private final VehicleBindingResolver bindingResolver;

    private final PositionBroadcaster broadcaster;

    private final TelemetryMetrics metrics;

    public void handle(String payload) {
        PositionEvent event = TelemetryJson.read(payload, PositionEvent.class);
        if (FUTURE.equals(event.timeQuality()) || STALE.equals(event.timeQuality())) {
            metrics.latestStale();
            return;
        }
        if (latestStore.updateIfNewer(event)) {
            metrics.latestUpdated();
            mileageStore.accumulate(event, bindingResolver.resolve(event.simNo()));
            broadcaster.broadcast(event);
            return;
        }
        metrics.latestStale();
        log.debug("旧版本位置未覆盖最新位置, simNo={}, eventId={}", event.simNo(), event.eventId());
    }
}
