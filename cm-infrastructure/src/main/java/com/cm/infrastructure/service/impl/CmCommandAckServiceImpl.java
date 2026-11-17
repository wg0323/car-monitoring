package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CommandAckEvent;
import com.cm.infrastructure.command.CommandStatus;
import com.cm.infrastructure.persistence.entity.CmCommandAckException;
import com.cm.infrastructure.persistence.entity.CmCommandAttempt;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmCommandAckExceptionMapper;
import com.cm.infrastructure.persistence.mapper.CmCommandAttemptMapper;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandAckService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 终端应答实现。
 * 状态推进用条件更新：影响行数为 1 才算处理，0 表示重复或迟到，终态不会被回退。
 * 映射缺失或应答消息 ID 与下行不匹配时只写异常记录，不做“最近一条指令”式猜测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmCommandAckServiceImpl implements CmCommandAckService {

    /** 位置信息查询应答，业务应答即成功。 */
    private static final int ACK_MESSAGE_ID_POSITION_QUERY = 0x0201;

    private static final int ACK_RESULT_SUCCESS = 0;

    private static final String REASON_NO_MAPPING = "NO_ATTEMPT_MAPPING";

    private static final String REASON_UNEXPECTED_ACK = "UNEXPECTED_ACK_MESSAGE";

    private static final String METRIC_ADVANCED = "cm.command.ack.advanced";

    private static final String METRIC_DUPLICATE = "cm.command.ack.duplicate";

    private static final String METRIC_LATE = "cm.command.ack.late";

    private static final String METRIC_UNMATCHED = "cm.command.ack.unmatched";

    private static final int RAW_HEX_MAX_LENGTH = 512;

    private static final int REASON_MAX_LENGTH = 255;

    private final CmCommandAttemptMapper attemptMapper;

    private final CmCommandAckExceptionMapper ackExceptionMapper;

    private final CmTerminalCommandMapper terminalCommandMapper;

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(CommandAckEvent event) {
        CmCommandAttempt attempt = attemptMapper.selectByRoute(event.gatewayId(), event.simNo(),
                event.terminalSerialNo(), event.connectionIncarnation());
        if (attempt == null) {
            recordException(event, "", REASON_NO_MAPPING);
            return;
        }
        Integer expected = attempt.getExpectedAckMessageId();
        if (expected != null && expected != 0 && expected != event.ackMessageId()) {
            recordException(event, attempt.getCommandId(), REASON_UNEXPECTED_ACK);
            return;
        }
        String targetStatus = resolveTargetStatus(event);
        int updated = terminalCommandMapper.advanceOnAck(attempt.getCommandId(), targetStatus, LocalDateTime.now());
        if (updated == 1) {
            count(METRIC_ADVANCED);
            log.debug("终端应答已推进状态, commandId={}, status={}", attempt.getCommandId(), targetStatus);
            return;
        }
        CmTerminalCommand current = terminalCommandMapper.selectByCommandId(attempt.getCommandId());
        if (current != null && CommandStatus.TERMINAL.contains(current.getCommandStatus())) {
            count(METRIC_LATE);
            log.info("迟到应答，终态不回退, commandId={}, status={}",
                    attempt.getCommandId(), current.getCommandStatus());
            return;
        }
        count(METRIC_DUPLICATE);
        log.info("重复应答，未推进状态, commandId={}, attemptNo={}",
                attempt.getCommandId(), attempt.getAttemptNo());
    }

    /**
     * 0x0201 属于业务应答即成功；通用 0x0001 结果为 0 只到「终端已应答」，失败则直接 FAILED。
     */
    private String resolveTargetStatus(CommandAckEvent event) {
        if (event.ackMessageId() == ACK_MESSAGE_ID_POSITION_QUERY) {
            return CommandStatus.SUCCESS;
        }
        return event.ackResult() == ACK_RESULT_SUCCESS ? CommandStatus.ACKED : CommandStatus.FAILED;
    }

    private void recordException(CommandAckEvent event, String commandId, String reason) {
        CmCommandAckException exception = new CmCommandAckException();
        exception.setSimNo(event.simNo());
        exception.setTerminalSerialNo(event.terminalSerialNo());
        exception.setConnectionIncarnation(event.connectionIncarnation());
        exception.setGatewayId(event.gatewayId());
        exception.setAckMessageId(event.ackMessageId());
        exception.setAckResult(event.ackResult());
        exception.setCommandId(commandId);
        exception.setReason(reason);
        exception.setRawHex(truncate(event.rawHex(), RAW_HEX_MAX_LENGTH));
        ackExceptionMapper.insert(exception);
        count(METRIC_UNMATCHED);
        log.warn("终端应答无法归属, simNo={}, serialNo={}, incarnation={}, ackMessageId={}, reason={}",
                event.simNo(), event.terminalSerialNo(), event.connectionIncarnation(),
                event.ackMessageId(), reason);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void count(String metricName) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(metricName).increment();
        }
    }
}
