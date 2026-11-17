package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmMapArea;
import com.cm.infrastructure.persistence.row.MapAreaRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 区域表 Mapper。数据范围过滤与部门名关联写在 mapper/MapAreaMapper.xml。
 */
public interface CmMapAreaMapper extends BaseMapper<CmMapArea> {

    List<MapAreaRow> selectMapAreaPage(@Param("scope") DataScope scope,
                                       @Param("name") String name,
                                       @Param("departmentId") Long departmentId,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    long countMapAreaPage(@Param("scope") DataScope scope,
                          @Param("name") String name,
                          @Param("departmentId") Long departmentId);

    /**
     * 按 ID 取范围内区域；越界返回 null，由上层转 404，不泄露其他部门数据是否存在。
     */
    MapAreaRow selectMapAreaInScope(@Param("scope") DataScope scope,
                                    @Param("id") long id);
}
