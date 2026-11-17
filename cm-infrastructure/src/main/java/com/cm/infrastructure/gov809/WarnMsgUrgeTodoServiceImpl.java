package com.cm.infrastructure.gov809;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cm.infrastructure.persistence.entity.CmWarnMsgUrgeTodoReq;
import com.cm.infrastructure.persistence.mapper.CmWarnMsgUrgeTodoReqMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 报警督办落库与应答推进。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarnMsgUrgeTodoServiceImpl implements WarnMsgUrgeTodoService {

    private final CmWarnMsgUrgeTodoReqMapper urgeTodoMapper;

    @Override
    public boolean record(CmWarnMsgUrgeTodoReq row) {
        if (row.getAckResult() == null) {
            row.setAckResult(0);
        }
        try {
            urgeTodoMapper.insert(row);
            return true;
        } catch (DuplicateKeyException ex) {
            log.info("督办已存在，跳过重复落库, platformId={}, superviseId={}",
                    row.getPlatformId(), row.getSuperviseId());
            return false;
        }
    }

    @Override
    public boolean acknowledge(long platformId, long superviseId, int result) {
        int updated = urgeTodoMapper.update(null, Wrappers.<CmWarnMsgUrgeTodoReq>lambdaUpdate()
                .eq(CmWarnMsgUrgeTodoReq::getPlatformId, platformId)
                .eq(CmWarnMsgUrgeTodoReq::getSuperviseId, superviseId)
                .isNull(CmWarnMsgUrgeTodoReq::getAckTime)
                .set(CmWarnMsgUrgeTodoReq::getAckResult, result)
                .set(CmWarnMsgUrgeTodoReq::getAckTime, LocalDateTime.now()));
        return updated == 1;
    }

    @Override
    public CmWarnMsgUrgeTodoReq find(long platformId, long superviseId) {
        return urgeTodoMapper.selectOne(Wrappers.<CmWarnMsgUrgeTodoReq>lambdaQuery()
                .eq(CmWarnMsgUrgeTodoReq::getPlatformId, platformId)
                .eq(CmWarnMsgUrgeTodoReq::getSuperviseId, superviseId)
                .last("LIMIT 1"));
    }
}
 
