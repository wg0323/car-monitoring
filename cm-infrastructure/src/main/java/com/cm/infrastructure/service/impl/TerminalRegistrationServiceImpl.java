package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.domain.registration.RegistrationVerdict;
import com.cm.infrastructure.persistence.entity.CmTerminal;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.mapper.CmTerminalMapper;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import com.cm.infrastructure.service.TerminalRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 注册判定实现。身份锚点用报文头的 SIM，不用终端自报的车牌：
 * 车牌是公开信息，若只比对车牌，任何设备报一个库里的车牌就能拿到「注册成功」和鉴权码。
 *
 * <ol>
 *   <li>按 SIM 找车（与旧实现 getVehicleData(simNo) 一致，cm_vehicle.sim_no 有索引，
 *       且 P2 装载数据里 10005 行中只有 1 行 sim_no 为空）；找不到 → 2。</li>
 *   <li>报文的车牌与车牌颜色必须与该车的记录一致，否则视为顶号 → 2。</li>
 *   <li>终端：车已绑终端时，要求绑定的终端号与报文终端号一致；车未绑终端时
 *       退回旧实现的「库里存在该终端号」→ 否则 4。</li>
 * </ol>
 *
 * <p>注册每连接一次，直接查库，不做缓存，避免引入新的缓存一致性而影响接入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalRegistrationServiceImpl implements TerminalRegistrationService {

    private static final long NO_TERMINAL = 0L;

    private final CmVehicleMapper vehicleMapper;

    private final CmTerminalMapper terminalMapper;

    @Override
    public RegistrationVerdict evaluate(String plateNo, int plateColor, String simNo, String terminalId) {
        CmVehicle vehicle = vehicleOf(simNo);
        if (vehicle == null) {
            log.info("终端注册被拒：该 SIM 未登记车辆, simNo={}, plateNo={}", simNo, plateNo);
            return RegistrationVerdict.VEHICLE_NOT_FOUND;
        }
        if (!matches(plateNo, plateColor, vehicle)) {
            log.info("终端注册被拒：车牌或颜色与该 SIM 登记的车不一致, simNo={}, plateNo={}, color={}",
                    simNo, plateNo, plateColor);
            return RegistrationVerdict.VEHICLE_NOT_FOUND;
        }
        if (!terminalExists(vehicle, terminalId)) {
            log.info("终端注册被拒：终端号与该车绑定关系不一致, simNo={}, terminalId={}", simNo, terminalId);
            return RegistrationVerdict.TERMINAL_NOT_FOUND;
        }
        return RegistrationVerdict.SUCCESS;
    }

    private CmVehicle vehicleOf(String simNo) {
        if (simNo == null || simNo.isBlank()) {
            return null;
        }
        return vehicleMapper.selectOne(Wrappers.<CmVehicle>lambdaQuery()
                .eq(CmVehicle::getSimNo, simNo)
                .last("LIMIT 1"));
    }

    /**
     * 报文字段与库内记录逐项比对；颜色是数值，不做字符串归一。
     */
    private boolean matches(String plateNo, int plateColor, CmVehicle vehicle) {
        if (plateNo == null || plateNo.isBlank() || !plateNo.equals(vehicle.getPlateNo())) {
            return false;
        }
        return vehicle.getPlateColor() != null && vehicle.getPlateColor() == plateColor;
    }

    private boolean terminalExists(CmVehicle vehicle, String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return false;
        }
        Long boundTerminalId = vehicle.getTerminalId();
        if (boundTerminalId != null && boundTerminalId != NO_TERMINAL) {
            CmTerminal bound = terminalMapper.selectById(boundTerminalId);
            return bound != null && terminalId.equals(bound.getTermNo());
        }
        Long count = terminalMapper.selectCount(Wrappers.<CmTerminal>lambdaQuery()
                .eq(CmTerminal::getTermNo, terminalId));
        return count != null && count > 0L;
    }
}
