package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CmCommandMqProperties;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.CommandDownlinkEvent;
import com.cm.infrastructure.command.CommandJson;
import com.cm.infrastructure.command.CommandStatus;
import com.cm.infrastructure.command.TerminalRoute;
import com.cm.infrastructure.command.TerminalRouteRegistry;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandOutboxLeaseService;
import com.cm.infrastructure.service.CmCommandOutboxRelayService;
import com.cm.infrastructure.service.CmCommandOutboxStateService;
import com.cm.infrastructure.service.CmCommandPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Outbox 转发实现。
 * 顺序固定为：抢占租约 → 校验指令仍可发 → 解析 Redis 路由 → 落 PUBLISHED 与消息体 → 发布。
 * 先落库再发布，崩溃后由租约超时回收重发；重发沿用同一 commandId。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.command.relay", name = "enabled", havingValue = "true")
public class CmCommandOutboxRelayServiceImpl implements CmCommandOutboxRelayService {

    private static final String ROUTING_KEY_PREFIX = "command.downlink.";

    private static final String REASON_NO_ROUTE = "NO_ROUTE";

    private static final int MAX_CAUSE_LENGTH = 200;

    private final CmCommandOutboxLeaseService leaseService;

    private final CmCommandOutboxStateService outboxStateService;

    private final CmTerminalCommandMapper terminalCommandMapper;

    private final TerminalRouteRegistry routeRegistry;

    private final CmCommandPublisher publisher;

    private final CmCommandProperties properties;

    private final CmCommandMqProperties mqProperties;

    private final String owner = resolveOwner();

    @Override
    @Scheduled(fixedDelayString = "${cm.command.relay.interval-millis:500}")
    public int relayOnce() {
        List<CmCommandOutbox> rows = leaseService.claim(owner);
        int published = 0;
        for (CmCommandOutbox row : rows) {
            try {
                if (relayOne(row)) {
                    published++;
                }
            } catch (RuntimeException ex) {
                log.error("Outbox 转发异常, commandId={}", row.getCommandId(), ex);
            }
        }
        return published;
    }

    private boolean relayOne(CmCommandOutbox row) {
        CmTerminalCommand command = terminalCommandMapper.selectByCommandId(row.getCommandId());
        if (command == null || CommandStatus.TERMINAL.contains(command.getCommandStatus())) {
            outboxStateService.skip(row);
            return false;
        }
        Optional<TerminalRoute> route = routeRegistry.find(command.getSimNo());
        if (route.isEmpty()) {
            outboxStateService.defer(row, REASON_NO_ROUTE);
            return false;
        }
        String routingKey = ROUTING_KEY_PREFIX + route.get().gatewayId();
        String payload = CommandJson.write(buildEvent(command, route.get(), routingKey));
        OptionalInt publishAttempt = outboxStateService.markPublished(row, mqProperties.getExchange(),
                routingKey, payload);
        if (publishAttempt.isEmpty()) {
            return false;
        }
        try {
            publisher.publishDownlink(command.getCommandId(), publishAttempt.getAsInt(), mqProperties.getExchange(),
                    routingKey, payload);
            return true;
        } catch (RuntimeException ex) {
            outboxStateService.fail(command.getCommandId(), publishAttempt.getAsInt(), cause(ex));
            return false;
        }
    }

    private CommandDownlinkEvent buildEvent(CmTerminalCommand command, TerminalRoute route, String routingKey) {
        return new CommandDownlinkEvent(CommandDownlinkEvent.SCHEMA_VERSION, command.getCommandId(),
                CommandDownlinkEvent.EVENT_TYPE, command.getCommandId(), command.getSimNo(),
                route.gatewayId(), command.getCommandType() == null ? 0 : command.getCommandType(),
                command.getCommandCode(), command.getCommandData(), routingKey,
                LocalDateTime.now().toString());
    }

    private String cause(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= MAX_CAUSE_LENGTH ? message : message.substring(0, MAX_CAUSE_LENGTH);
    }

    private static String resolveOwner() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }
}
