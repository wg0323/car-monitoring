package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.command.CommandQuery;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 终端指令 Mapper。状态推进全部是条件更新，影响行数为 0 表示重复、迟到或不满足迁移条件。
 */
public interface CmTerminalCommandMapper extends BaseMapper<CmTerminalCommand> {

    CmTerminalCommand selectByCommandId(@Param("commandId") String commandId);

    int markSentMq(@Param("commandId") String commandId);

    int markSentTerminal(@Param("commandId") String commandId,
                         @Param("attemptNo") int attemptNo,
                         @Param("terminalSerialNo") int terminalSerialNo,
                         @Param("sentAt") LocalDateTime sentAt,
                         @Param("expireAt") LocalDateTime expireAt);

    /**
     * 终端应答推进状态：只允许从已发 MQ / 已发终端推进，终态不会被回退。
     */
    int advanceOnAck(@Param("commandId") String commandId,
                     @Param("targetStatus") String targetStatus,
                     @Param("ackAt") LocalDateTime ackAt);

    int markTimeout(@Param("commandId") String commandId, @Param("timeoutAt") LocalDateTime timeoutAt);

    /**
     * 平台侧确定性失败（例如未注册的指令编码），不写 ack_at，只留备注。
     */
    int markRejected(@Param("commandId") String commandId, @Param("remark") String remark);

    /**
     * 已发终端但超过本次尝试超时时间的指令，用于置 TIMEOUT。
     */
    List<CmTerminalCommand> selectExpiredSent(@Param("timeoutAt") LocalDateTime timeoutAt,
                                              @Param("size") int size);

    /**
     * 按数据范围分页查询命令结果，创建时间倒序由雪花 ID 倒序保证。
     */
    List<CmTerminalCommand> selectPageByScope(@Param("scope") DataScope scope,
                                              @Param("query") CommandQuery query,
                                              @Param("offset") long offset,
                                              @Param("size") long size);

    long countByScope(@Param("scope") DataScope scope, @Param("query") CommandQuery query);
}
