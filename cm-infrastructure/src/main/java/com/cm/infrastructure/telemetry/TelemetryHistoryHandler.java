package com.cm.infrastructure.telemetry;

import com.cm.domain.position.TimeQuality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 历史轨迹消费。先抢占 Redis 去重键再写库：占用成功才处理，写库失败必须释放键，
 * 否则本次重试会被误判为重复而静默丢一条轨迹。
 * 严重异常时间不写主集合，转隔离集合后正常确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryHistoryHandler {

    private static final String FUTURE = TimeQuality.FUTURE.name();

    private static final String STALE = TimeQuality.STALE.name();

    private final PositionDedupStore dedupStore;

    private final PositionHistoryRepository historyRepository;

    private final PositionQuarantineRepository quarantineRepository;

    private final VehicleBindingResolver bindingResolver;

    private final TelemetryMetrics metrics;

    public void handle(String payload) {
        PositionEvent event = TelemetryJson.read(payload, PositionEvent.class);
        if (!dedupStore.acquire(event.eventId())) {
            metrics.dedupHit();
            log.debug("重复定位事件已跳过, eventId={}", event.eventId());
            return;
        }
        try {
            if (isQuarantined(event)) {
                quarantineRepository.insert(event);
                metrics.quarantined();
                log.warn("异常时间事件已隔离, eventId={}, simNo={}, quality={}, deviceTime={}, ingestTime={}",
                        event.eventId(), event.simNo(), event.timeQuality(), event.deviceTime(),
                        event.ingestTime());
                return;
            }
            VehicleBinding binding = bindingResolver.resolve(event.simNo());
            historyRepository.insert(event, binding);
            metrics.historySaved();
        } catch (RuntimeException ex) {
            releaseQuietly(event.eventId());
            throw ex;
        }
    }

    private boolean isQuarantined(PositionEvent event) {
        return FUTURE.equals(event.timeQuality()) || STALE.equals(event.timeQuality());
    }

    /**
     * 释放失败不能再抛异常覆盖原始写入异常，只记录后交给容器重试；键仍在时重试会被判重，
     * 此时宁可丢这一条也不重复写，日志保留事件 ID 供死信排查。
     */
    private void releaseQuietly(String eventId) {
        try {
            dedupStore.release(eventId);
        } catch (RuntimeException ex) {
            log.error("去重键释放失败，重试可能被误判为重复, eventId={}", eventId, ex);
        }
    }
}
