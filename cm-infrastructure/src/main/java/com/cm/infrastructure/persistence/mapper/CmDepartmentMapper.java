package com.cm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmDepartment;
import com.cm.infrastructure.persistence.row.DepartmentDetailRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门（车辆分组）Mapper。管理页的树表与校验查询写在 mapper/DepartmentMapper.xml。
 */
public interface CmDepartmentMapper extends BaseMapper<CmDepartment> {

    /**
     * 数据范围内的全部分组，keyword 按名称前缀匹配。
     */
    List<DepartmentDetailRow> selectDepartmentsInScope(@Param("scope") DataScope scope,
                                                       @Param("keyword") String keyword);

    /**
     * 单个分组，范围外或已删返回 null。
     */
    DepartmentDetailRow selectDepartmentInScope(@Param("scope") DataScope scope, @Param("id") long id);

    /**
     * 名称是否已存在。uk_name 覆盖逻辑删除行，这里也不过滤 is_deleted。
     */
    long countByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * 未删除的直接子组数量。
     */
    long countChildren(@Param("id") long id);

    /**
     * 该分组下未删除的车辆数量。
     */
    long countVehicles(@Param("id") long id);

    /**
     * 全部下级分组 ID（含多层），用于阻止把上级改成自己的下级。
     */
    List<Long> selectDescendantIds(@Param("id") long id);
}
