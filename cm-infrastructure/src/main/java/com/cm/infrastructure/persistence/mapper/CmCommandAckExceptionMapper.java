package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmCommandAckException;

/**
 * 无法归属的终端应答 Mapper，只有插入与查询，没有回填归属的写路径。
 */
public interface CmCommandAckExceptionMapper extends BaseMapper<CmCommandAckException> {
}
