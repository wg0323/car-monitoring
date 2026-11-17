package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmMqDeadLetter;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * MQ 死信记录 Mapper。重放用条件更新，保证同一死信不会被并发重放两次。
 */
public interface CmMqDeadLetterMapper extends BaseMapper<CmMqDeadLetter> {

    int markReplayed(@Param("id") Long id, @Param("replayedAt") LocalDateTime replayedAt);
}
