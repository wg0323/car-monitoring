package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmMapArea;
import com.cm.infrastructure.persistence.row.MapAreaRow;

/**
 * 区域持久层服务。数据范围只接受服务端构造的 DataScope，不接受请求参数。
 */
public interface CmMapAreaService extends IService<CmMapArea> {

    IPage<MapAreaRow> pageByScope(DataScope scope, long pageNo, long pageSize, String name, Long departmentId);

    /**
     * 取范围内区域；越界或已删返回 null。
     */
    MapAreaRow getInScope(DataScope scope, long id);
}
