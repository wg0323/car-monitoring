package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmCommandAttempt;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 指令下行尝试 Mapper。ACK 只能按 (sim_no, terminal_serial_no, connection_incarnation) 回查，
 * 没有映射时由调用方落异常记录，不做“最近一条指令”式猜测。
 */
public interface CmCommandAttemptMapper extends BaseMapper<CmCommandAttempt> {

    /**
     * 下一尝试序号；同一网关内下行消费串行，跨实例由唯一键兜底。
     */
    int selectNextAttemptNo(@Param("commandId") String commandId);

    CmCommandAttempt selectByRoute(@Param("gatewayId") String gatewayId,
                                   @Param("simNo") String simNo,
                                   @Param("terminalSerialNo") int terminalSerialNo,
                                   @Param("connectionIncarnation") long connectionIncarnation);

    int markWritten(@Param("id") Long id, @Param("writeAt") LocalDateTime writeAt);

    int updateWriteStatus(@Param("id") Long id,
                          @Param("writeStatus") String writeStatus,
                          @Param("errorReason") String errorReason);
}
