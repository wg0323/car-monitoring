package com.cm.integration.forward;

import com.cm.infrastructure.gov809.Gov809LinkService;
import com.cm.infrastructure.gov809.Gov809OutboxService;
import com.cm.integration.config.Gov809Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 检查点刷盘。单链路每秒可能上千条位置，逐条更新 cm_gov809_link 会把数据库打成瓶颈，
 * 这里按固定间隔把「已分配最大序号 / 已发送最大序号 / 积压条数」一次性写回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Gov809CheckpointFlusher {

    private final Gov809OutboxService outboxService;

    private final Gov809LinkService linkService;

    private final Gov809Properties properties;

    @Scheduled(initialDelayString = "${cm.gov809.checkpoint-flush-millis:1000}",
            fixedDelayString = "${cm.gov809.checkpoint-flush-millis:1000}")
    public void flush() {
        long platformId = properties.getPlatformId();
        try {
            linkService.flushCheckpoint(platformId, Gov809LinkService.ROLE_ENTERPRISE_UPSTREAM,
                    outboxService.maxAllocatedSeq(platformId), outboxService.maxSentSeq(platformId),
                    outboxService.countPending(platformId));
        } catch (RuntimeException ex) {
            log.warn("809 检查点刷盘失败，下一轮重试, platformId={}", platformId, ex);
        }
    }
}
 
