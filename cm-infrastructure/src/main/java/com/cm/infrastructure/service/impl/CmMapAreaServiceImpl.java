package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.domain.scope.QueryLimits;
import com.cm.infrastructure.persistence.entity.CmMapArea;
import com.cm.infrastructure.persistence.mapper.CmMapAreaMapper;
import com.cm.infrastructure.persistence.row.MapAreaRow;
import com.cm.infrastructure.service.CmMapAreaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 区域查询实现。复杂关联 SQL 由 MapAreaMapper.xml 提供，不在 Service 里拼条件。
 */
@Slf4j
@Service
public class CmMapAreaServiceImpl extends ServiceImpl<CmMapAreaMapper, CmMapArea> implements CmMapAreaService {

    @Override
    public IPage<MapAreaRow> pageByScope(DataScope scope, long pageNo, long pageSize, String name,
                                         Long departmentId) {
        requireScope(scope);
        long offset = computeOffset(pageNo, pageSize);
        long total = baseMapper.countMapAreaPage(scope, name, departmentId);
        List<MapAreaRow> rows = baseMapper.selectMapAreaPage(scope, name, departmentId, offset, pageSize);
        Page<MapAreaRow> page = new Page<>(pageNo, pageSize, total);
        page.setRecords(rows);
        return page;
    }

    @Override
    public MapAreaRow getInScope(DataScope scope, long id) {
        requireScope(scope);
        return baseMapper.selectMapAreaInScope(scope, id);
    }

    private void requireScope(DataScope scope) {
        if (scope == null) {
            throw new CmBusinessException("数据范围不能为空");
        }
    }

    private long computeOffset(long pageNo, long pageSize) {
        if (pageNo < 1 || pageNo > QueryLimits.MAX_PAGE_NO
                || pageSize < 1 || pageSize > QueryLimits.MAX_PAGE_SIZE) {
            throw new CmBusinessException("分页参数超出允许范围");
        }
        try {
            return Math.multiplyExact(pageNo - 1, pageSize);
        } catch (ArithmeticException ex) {
            throw new CmBusinessException("分页偏移量溢出", ex);
        }
    }
}
