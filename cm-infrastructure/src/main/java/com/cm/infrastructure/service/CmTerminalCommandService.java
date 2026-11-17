package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.domain.command.CommandQuery;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.dto.CommandAcceptCommand;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;

/**
 * 指令受理。指令记录与 Outbox 必须在同一事务写入，沿用同一个服务端生成的 commandId，
 * 且必须校验目标车辆在当前数据范围内。
 */
public interface CmTerminalCommandService extends IService<CmTerminalCommand> {

    String accept(DataScope scope, CommandAcceptCommand command);

    /**
     * 命令结果分页查询，数据范围与车辆列表同一口径。
     */
    IPage<CmTerminalCommand> pageByScope(DataScope scope, CommandQuery query, long pageNo, long pageSize);
}
