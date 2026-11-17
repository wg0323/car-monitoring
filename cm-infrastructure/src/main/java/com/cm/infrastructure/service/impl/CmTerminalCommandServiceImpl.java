package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.domain.command.CommandQuery;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.converter.CommandConverter;
import com.cm.infrastructure.dto.CommandAcceptCommand;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.mapper.CmCommandOutboxMapper;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import com.cm.infrastructure.service.CmTerminalCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 指令受理实现。缺参必须显式失败：旧系统 25 个指令面板缺参 500，且不能用 defaultValue=0 兜底，
 * 否则会误下发到 0 号车。权限字段与消息路由全部由服务端生成，不接受客户端自带值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmTerminalCommandServiceImpl extends ServiceImpl<CmTerminalCommandMapper, CmTerminalCommand>
        implements CmTerminalCommandService {

    private static final long MIN_VEHICLE_ID = 1L;

    private static final int MAX_COMMAND_CODE_LENGTH = 64;

    private static final int MAX_COMMAND_DATA_LENGTH = 255;

    private final CmCommandOutboxMapper commandOutboxMapper;

    private final CmVehicleMapper vehicleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String accept(DataScope scope, CommandAcceptCommand command) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
        validate(command);
        CmVehicle vehicle = vehicleMapper.selectById(command.getVehicleId());
        if (vehicle == null || Boolean.TRUE.equals(vehicle.getDeleted())) {
            throw new CmBusinessException("目标车辆不存在");
        }
        if (scope.hasDepartmentFilter() && !scope.getDepartmentIds().contains(vehicle.getDepartmentId())) {
            throw new CmBusinessException("无权向该车辆下发指令");
        }
        String commandId = UUID.randomUUID().toString();
        save(CommandConverter.toEntity(commandId, vehicle, command, scope.getUserId()));
        commandOutboxMapper.insert(CommandConverter.toOutbox(commandId));
        log.info("指令已受理, commandId={}, vehicleId={}", commandId, command.getVehicleId());
        return commandId;
    }

    @Override
    public IPage<CmTerminalCommand> pageByScope(DataScope scope, CommandQuery query, long pageNo, long pageSize) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
        if (query == null) {
            throw new CmBusinessException("查询条件不能为空");
        }
        long offset = computeOffset(pageNo, pageSize);
        long total = baseMapper.countByScope(scope, query);
        List<CmTerminalCommand> rows = baseMapper.selectPageByScope(scope, query, offset, pageSize);
        Page<CmTerminalCommand> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    private long computeOffset(long pageNo, long pageSize) {
        if (pageNo < 1 || pageNo > QueryLimits.MAX_PAGE_NO
                || pageSize < 1 || pageSize > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("分页参数超出允许范围");
        }
        try {
            return Math.multiplyExact(pageNo - 1, pageSize);
        } catch (ArithmeticException ex) {
            throw new CmBusinessException("分页偏移量溢出", ex);
        }
    }

    private void validate(CommandAcceptCommand command) {
        if (command == null || command.getVehicleId() == null
                || command.getVehicleId() < MIN_VEHICLE_ID) {
            throw new CmBusinessException("必须指定有效目标车辆");
        }
        if (command.getCommandCode() == null || command.getCommandCode().isBlank()
                || command.getCommandCode().length() > MAX_COMMAND_CODE_LENGTH) {
            throw new CmBusinessException("指令编码不能为空且不能超过 " + MAX_COMMAND_CODE_LENGTH + " 字符");
        }
        if (command.getCommandData() != null
                && command.getCommandData().length() > MAX_COMMAND_DATA_LENGTH) {
            throw new CmBusinessException("指令参数不能超过 " + MAX_COMMAND_DATA_LENGTH + " 字符");
        }
    }
}
