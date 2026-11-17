package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.infrastructure.persistence.entity.CmGov809Outbox;

/** 809 断链积压与补发队列。待发批次、最大序号与容量裁剪都用 Wrapper 实现，不另建 XML。 */
public interface CmGov809OutboxMapper extends BaseMapper<CmGov809Outbox> {
}
 
