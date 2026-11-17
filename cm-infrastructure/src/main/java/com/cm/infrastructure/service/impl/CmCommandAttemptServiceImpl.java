package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.AttemptStatus;
import com.cm.infrastructure.persistence.entity.CmCommandAttempt;
import com.cm.infrastructure.persistence.mapper.CmCommandAttemptMapper;
import com.cm.infrastructure.service.CmCommandAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 指令下行尝试实现。
 * 尝试序号按 MAX(attempt_no)+1 分配，唯一键 uk_command_attempt 兜底重复投递，重复事件不会写第二帧。
 */
@Service
@RequiredArgsConstructor
public class CmCommandAttemptServiceImpl implements CmCommandAttemptService {

    private static final int REASON_MAX_LENGTH = 255;

    private final CmCommandAttemptMapper attemptMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CmCommandAttempt register(String commandId, String simNo, int terminalSerialNo,
                                     long connectionIncarnation, String gatewayId,
                                     int downlinkMessageId, int expectedAckMessageId) {
        CmCommandAttempt attempt = new CmCommandAttempt();
        attempt.setCommandId(commandId);
        attempt.setAttemptNo(attemptMapper.selectNextAttemptNo(commandId));
        attempt.setSimNo(simNo);
        attempt.setTerminalSerialNo(terminalSerialNo);
        attempt.setConnectionIncarnation(connectionIncarnation);
        attempt.setGatewayId(gatewayId);
        attempt.setDownlinkMessageId(downlinkMessageId);
        attempt.setExpectedAckMessageId(expectedAckMessageId);
        attempt.setWriteStatus(AttemptStatus.PERSISTED);
        attempt.setErrorReason("");
        attemptMapper.insert(attempt);
        return attempt;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markWritten(Long id) {
        attemptMapper.markWritten(id, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markWriteFailure(Long id, String writeStatus, String reason) {
        String safeReason = reason == null ? "" : reason;
        attemptMapper.updateWriteStatus(id, writeStatus,
                safeReason.length() <= REASON_MAX_LENGTH ? safeReason : safeReason.substring(0, REASON_MAX_LENGTH));
    }
}
