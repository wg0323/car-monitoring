package com.cm.admin.service;

import com.cm.admin.common.PageResult;
import com.cm.admin.vo.GovCommandVO;
import com.cm.admin.vo.LinkStateVO;
import com.cm.domain.command.GovCommandQuery;

/**
 * 809 监管平台命令应用服务。页面数据直接读 cm_jt809_command：
 * 本端下发的链路命令与上级下发的查岗、督办等报文共用一张表。
 */
public interface GovCommandAppService {

    PageResult<GovCommandVO> page(long pageNo, long pageSize, GovCommandQuery query);

    /**
     * 写入一条待下发的链路命令，由企业侧链路进程抢占执行。
     */
    Long sendLinkCommand(Long userId, int linkCmd);

    /**
     * 企业侧主从链路状态快照。
     */
    LinkStateVO linkState();
}
