package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.command.GovCommandQuery;
import com.cm.infrastructure.persistence.entity.CmJt809Command;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 指令与报文日志。P4-3 起同时承担链路命令队列：
 * 管理端写「待下发」，企业侧执行器抢占后置「执行中」并回写结果。
 */
public interface CmJt809CommandMapper extends BaseMapper<CmJt809Command> {

    List<CmJt809Command> selectPageByQuery(@Param("query") GovCommandQuery query,
                                           @Param("offset") long offset,
                                           @Param("size") long size);

    long countByQuery(@Param("query") GovCommandQuery query);

    /**
     * 待执行的链路命令，按主键升序保证先进先出。
     */
    List<CmJt809Command> selectPending(@Param("limit") int limit);

    /**
     * 条件抢占：只有仍处于「待下发」的行才会被置为「执行中」，多实例不会重复执行。
     */
    int claimForExecution(@Param("id") long id);

    /**
     * 回写执行结果，只接受从「执行中」推进。
     */
    int markExecutionResult(@Param("id") long id,
                            @Param("status") String status,
                            @Param("descr") String descr);

    /**
     * 回收卡在「执行中」的行：抢占后进程被杀或回写异常时，该行既不会重试也不会出现在待下发里，
     * 只能由这个定时回收置为失败，避免永久卡死。
     */
    int recoverStaleExecution(@Param("staleBefore") LocalDateTime staleBefore);
}
