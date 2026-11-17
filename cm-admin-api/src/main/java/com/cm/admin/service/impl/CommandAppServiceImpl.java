package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.CommandViewConverter;
import com.cm.admin.service.CommandAppService;
import com.cm.admin.vo.CommandVehicleVO;
import com.cm.admin.vo.CommandVO;
import com.cm.domain.command.CommandQuery;
import com.cm.domain.exception.CmConflictException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.dto.CommandAcceptCommand;
import com.cm.infrastructure.monitor.MonitorPositionStore;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.row.VehicleRow;
import com.cm.infrastructure.service.CmTerminalCommandService;
import com.cm.infrastructure.service.CmVehicleService;
import com.cm.infrastructure.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 终端指令实现。点名的在线判定与实时监控页共用 Redis 在线租约，
 * 保证页面上「在线」的车辆与可点名的车辆是同一份口径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandAppServiceImpl implements CommandAppService {

    /** 0x8201 位置信息查询。 */
    private static final int POSITION_QUERY_TYPE = 0x8201;

    private static final String POSITION_QUERY_CODE = "0x8201";

    private static final long CANDIDATE_LIMIT = 20L;

    private final CmTerminalCommandService terminalCommandService;

    private final CmVehicleService vehicleService;

    private final DataScopeService dataScopeService;

    private final MonitorPositionStore positionStore;

    @Override
    public String sendPositionQuery(Long userId, Long vehicleId) {
        DataScope scope = dataScopeService.resolve(userId);
        List<VehicleRow> rows = vehicleService.listInScopeByIds(scope, List.of(vehicleId));
        if (rows.isEmpty()) {
            throw new AccessDeniedException("无权向该车辆下发指令");
        }
        VehicleRow vehicle = rows.get(0);
        if (vehicle.getSimNo() == null || !positionStore.online(vehicle.getSimNo())) {
            throw new CmConflictException("车辆不在线，无法下发点名");
        }
        CommandAcceptCommand command = new CommandAcceptCommand();
        command.setVehicleId(vehicleId);
        command.setCommandType(POSITION_QUERY_TYPE);
        command.setCommandCode(POSITION_QUERY_CODE);
        command.setCommandData("");
        String commandId = terminalCommandService.accept(scope, command);
        log.info("点名已受理, userId={}, vehicleId={}, commandId={}", userId, vehicleId, commandId);
        return commandId;
    }

    @Override
    public PageResult<CommandVO> page(Long userId, long pageNo, long pageSize, CommandQuery query) {
        DataScope scope = dataScopeService.resolve(userId);
        IPage<CmTerminalCommand> page = terminalCommandService.pageByScope(scope, query, pageNo, pageSize);
        List<CommandVO> records = page.getRecords().stream()
                .map(CommandViewConverter::toCommandVO)
                .toList();
        log.debug("终端命令结果, userId={}, total={}, size={}", userId, page.getTotal(), records.size());
        return new PageResult<>(page.getTotal(), pageNo, pageSize, records);
    }

    @Override
    public List<CommandVehicleVO> candidates(Long userId, String keyword) {
        DataScope scope = dataScopeService.resolve(userId);
        IPage<VehicleRow> page = vehicleService.pageByScope(scope, 1, CANDIDATE_LIMIT, keyword, null, null);
        List<VehicleRow> rows = page.getRecords();
        List<String> simNos = rows.stream()
                .map(VehicleRow::getSimNo)
                .filter(simNo -> simNo != null && !simNo.isBlank())
                .distinct()
                .toList();
        Map<String, Boolean> onlineStates = positionStore.onlineAll(simNos);
        return rows.stream()
                .map(row -> CommandViewConverter.toCommandVehicleVO(row,
                        Boolean.TRUE.equals(onlineStates.get(row.getSimNo()))))
                .toList();
    }
}
