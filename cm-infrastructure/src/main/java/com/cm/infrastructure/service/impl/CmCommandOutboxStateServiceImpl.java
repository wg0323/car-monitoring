package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.CommandRetryPolicy;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.mapper.CmCommandOutboxMapper;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandOutboxStateService;
import com.cm.infrastructure.service.CmMqDeadLetterService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalInt;

/**
 * Outbox 状态推进实现。
 * 每条迁移都用 (id, 发布尝试序号) 做条件，旧回调、重复回调和超时回收都只能命中当前尝试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmCommandOutboxStateServiceImpl implements CmCommandOutboxStateService {

    private static final String METRIC_CONFIRMED = "cm.command.outbox.confirmed";

    private static final String METRIC_FAILED = "cm.command.outbox.failed";

    private static final String METRIC_SKIPPED = "cm.command.outbox.skipped";

    private static final String METRIC_RECOVERED = "cm.command.outbox.recovered";

    private static final String REASON_CONFIRM_TIMEOUT = "CONFIRM_TIMEOUT";

    private final CmCommandOutboxMapper outboxMapper;

    private final CmTerminalCommandMapper terminalCommandMapper;

    private final CmMqDeadLetterService deadLetterService;

    private final CmCommandProperties properties;

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OptionalInt markPublished(CmCommandOutbox row, String exchangeName, String routingKey, String payload) {
        int previousAttempt = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int publishAttempt = previousAttempt + 1;
        int updated = outboxMapper.markPublished(row.getId(), previousAttempt, publishAttempt,
                exchangeName, routingKey, payload, properties.getRelay().getConfirmTimeoutSeconds());
        return updated == 1 ? OptionalInt.of(publishAttempt) : OptionalInt.empty();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String commandId, int publishAttempt) {
        CmCommandOutbox row = outboxMapper.selectByCommandId(commandId);
        if (row == null) {
            return;
        }
        if (outboxMapper.markConfirmed(row.getId(), publishAttempt) != 1) {
            log.debug("confirm 与当前发布尝试不匹配，忽略, commandId={}, attempt={}", commandId, publishAttempt);
            return;
        }
        terminalCommandMapper.markSentMq(commandId);
        count(METRIC_CONFIRMED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fail(String commandId, int publishAttempt, String reason) {
        CmCommandOutbox row = outboxMapper.selectByCommandId(commandId);
        if (row == null) {
            return;
        }
        applyFailure(row, publishAttempt, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void defer(CmCommandOutbox row, String reason) {
        int previousAttempt = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int attempt = previousAttempt + 1;
        if (attempt >= properties.getRelay().getMaxAttempts()) {
            if (outboxMapper.markFailed(row.getId(), previousAttempt) == 1) {
                deadLetterService.parkOutboxFailure(row, attempt, reason);
                count(METRIC_FAILED);
                log.warn("指令未发布即耗尽重试, commandId={}, attempt={}, reason={}",
                        row.getCommandId(), attempt, reason);
            }
            return;
        }
        outboxMapper.markDeferred(row.getId(), previousAttempt, attempt, backoffMillis(attempt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void skip(CmCommandOutbox row) {
        int attempt = row.getRetryCount() == null ? 0 : row.getRetryCount();
        if (outboxMapper.markSkipped(row.getId(), attempt) == 1) {
            count(METRIC_SKIPPED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverExpired(int batchSize) {
        List<CmCommandOutbox> expired = outboxMapper.selectExpiredPublished(batchSize);
        int recovered = 0;
        for (CmCommandOutbox row : expired) {
            int attempt = row.getRetryCount() == null ? 0 : row.getRetryCount();
            if (applyFailure(row, attempt, REASON_CONFIRM_TIMEOUT)) {
                recovered++;
            }
        }
        if (recovered > 0) {
            count(METRIC_RECOVERED);
        }
        return recovered;
    }

    /**
     * 发布侧的失败处理：未耗尽按退避回到 PENDING，耗尽则 FAILED 并保留 payload 落死信。
     */
    private boolean applyFailure(CmCommandOutbox row, int publishAttempt, String reason) {
        if (publishAttempt >= properties.getRelay().getMaxAttempts()) {
            if (outboxMapper.markFailed(row.getId(), publishAttempt) != 1) {
                return false;
            }
            deadLetterService.parkOutboxFailure(row, publishAttempt, reason);
            count(METRIC_FAILED);
            log.warn("指令发布重试耗尽, commandId={}, attempt={}, reason={}",
                    row.getCommandId(), publishAttempt, reason);
            return true;
        }
        boolean updated = outboxMapper.markPendingRetry(row.getId(), publishAttempt,
                backoffMillis(publishAttempt)) == 1;
        if (updated) {
            log.warn("指令发布失败待重试, commandId={}, attempt={}, reason={}",
                    row.getCommandId(), publishAttempt, reason);
        }
        return updated;
    }

    private long backoffMillis(int attempt) {
        return CommandRetryPolicy.backoffMillis(attempt,
                properties.getRelay().getBackoffBaseMillis(),
                properties.getRelay().getBackoffMaxMillis());
    }

    private void count(String metricName) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(metricName).increment();
        }
    }
}
