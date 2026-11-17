package com.cm.infrastructure.service;

import com.cm.infrastructure.persistence.entity.CmCommandAttempt;

/**
 * 指令下行尝试的持久化入口。网关必须先调用 register 落映射，再写 Socket。
 */
public interface CmCommandAttemptService {

    /**
     * 分配下一个尝试序号并落 PERSISTED 记录。
     */
    CmCommandAttempt register(String commandId, String simNo, int terminalSerialNo, long connectionIncarnation,
                              String gatewayId, int downlinkMessageId, int expectedAckMessageId);

    void markWritten(Long id);

    void markWriteFailure(Long id, String writeStatus, String reason);
}
