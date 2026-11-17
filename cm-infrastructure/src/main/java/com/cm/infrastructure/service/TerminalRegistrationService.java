package com.cm.infrastructure.service;

import com.cm.domain.registration.RegistrationVerdict;

/**
 * 终端注册判定。注册每连接一次，直接查库，不做缓存。
 */
public interface TerminalRegistrationService {

    /**
     * @param plateNo    注册报文里的车牌号
     * @param plateColor 注册报文里的车牌颜色
     * @param simNo      报文头里的 SIM，用作终端存在性的兜底判据
     * @param terminalId 注册报文里的终端 ID
     */
    RegistrationVerdict evaluate(String plateNo, int plateColor, String simNo, String terminalId);
}
