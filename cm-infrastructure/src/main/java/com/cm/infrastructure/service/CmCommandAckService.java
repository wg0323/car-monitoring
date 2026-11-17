package com.cm.infrastructure.service;

import com.cm.infrastructure.command.CommandAckEvent;

/**
 * 终端应答处理。按连接三元组回查尝试映射，无法匹配时只落异常记录，不猜归属。
 */
public interface CmCommandAckService {

    void handle(CommandAckEvent event);
}
