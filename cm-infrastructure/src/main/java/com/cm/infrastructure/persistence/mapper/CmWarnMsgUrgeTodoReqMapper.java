package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 809 报警督办请求与应答。分页与增量查询写在 mapper/WarnMsgUrgeTodoMapper.xml。
 */
public interface CmWarnMsgUrgeTodoReqMapper extends BaseMapper<CmWarnMsgUrgeTodoReq> {

    List<CmWarnMsgUrgeTodoReq> selectUrgeTodoPage(@Param("plateNo") String plateNo,
                                                  @Param("acknowledged") Boolean acknowledged,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime,
                                                  @Param("offset") long offset,
                                                  @Param("size") long size);

    long countUrgeTodoPage(@Param("plateNo") String plateNo,
                           @Param("acknowledged") Boolean acknowledged,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime);

    /**
     * 增量督办：按主键升序取 sinceId 之后、不早于 startTime 的记录，供页面自动弹窗。
     */
    List<CmWarnMsgUrgeTodoReq> selectNewUrgeTodos(@Param("sinceId") long sinceId,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("limit") int limit);
}
