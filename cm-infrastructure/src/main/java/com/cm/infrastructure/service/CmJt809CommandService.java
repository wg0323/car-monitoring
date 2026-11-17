package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.domain.command.GovCommandQuery;
import com.cm.infrastructure.persistence.entity.CmJt809Command;

import java.util.List;

/**
 * 809 指令与报文日志。查询供页面展示，链路命令由管理端写「待下发」、
 * 企业侧链路进程抢占执行，两个进程只通过这张表交接。
 */
public interface CmJt809CommandService extends IService<CmJt809Command> {

    IPage<CmJt809Command> pageByQuery(GovCommandQuery query, long pageNo, long pageSize);

    /**
     * 写入一条待下发的链路命令，返回主键。
     */
    Long acceptLinkCommand(long platformId, int linkCmd, Long userId);

    /**
     * 写入一条待下发的报警督办应答（0x1401），返回主键。结果码为人工处置结论，调用方传入。
     */
    Long acceptUrgeTodoAck(long platformId, long superviseId, String plateNo, int plateColor,
                           int result, Long userId);

    /**
     * 待执行命令，按主键升序。
     */
    List<CmJt809Command> listPending(int limit);

    /**
     * 条件抢占，true 表示本次抢到执行权。
     */
    boolean claim(long id);

    /**
     * 回写执行结果，只接受从「执行中」推进。
     */
    boolean markResult(long id, String status, String descr);

    /**
     * 回收卡在「执行中」超过指定分钟数的命令，返回处理条数。
     */
    int recoverStaleExecution(int staleMinutes);
}
