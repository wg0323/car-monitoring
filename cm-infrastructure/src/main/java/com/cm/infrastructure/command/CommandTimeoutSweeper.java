package com.cm.infrastructure.command;

import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandOutboxStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时清扫。
 * 两件事：把超过本次尝试 expire_at 的指令置 TIMEOUT；回收 confirm 超时的 Outbox 记录。
 * 两类处理都是条件更新，多实例同时执行不会互相覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.command.relay", name = "sweep-enabled", havingValue = "true",
        matchIfMissing = true)
public class CommandTimeoutSweeper {

    private static final int BATCH_SIZE = 200;

    private final CmTerminalCommandMapper terminalCommandMapper;

    private final CmCommandOutboxStateService outboxStateService;

    @Scheduled(fixedDelayString = "${cm.command.relay.sweep-interval-millis:30000}")
    public void sweep() {
        int timedOut = markTimeouts();
        int recovered = outboxStateService.recoverExpired(BATCH_SIZE);
        if (timedOut > 0 || recovered > 0) {
            log.info("超时清扫完成, timedOut={}, outboxRecovered={}", timedOut, recovered);
        }
    }

    /**
     * 返回置为 TIMEOUT 的条数。
     */
    public int markTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<CmTerminalCommand> expired = terminalCommandMapper.selectExpiredSent(now, BATCH_SIZE);
        int updated = 0;
        for (CmTerminalCommand command : expired) {
            updated += terminalCommandMapper.markTimeout(command.getCommandId(), now);
        }
        return updated;
    }
}
