package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.CommandVehicleVO;
import com.cm.admin.vo.CommandVO;
import com.cm.domain.command.CommandQuery;

import java.util.List;

/**
 * 终端指令应用服务。点名是即时动作：车辆不在线直接拒绝，
 * 不把命令挂进 Outbox 等待上线后补发。
 */
public interface CommandAppService {

    /**
     * 下发点名（0x8201 位置信息查询），返回服务端生成的 commandId。
     */
    String sendPositionQuery(Long userId, Long vehicleId);

    PageResult<CommandVO> page(Long userId, long pageNo, long pageSize, CommandQuery query);

    /**
     * 可下发指令的车辆候选，按车牌前缀搜索，带在线态。
     */
    List<CommandVehicleVO> candidates(Long userId, String keyword);
}
