package com.cm.infrastructure.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmDepartment;
import com.cm.infrastructure.persistence.row.DepartmentDetailRow;

import java.util.List;

/**
 * 部门（车辆分组）持久层服务。管理页的树表与删除、层级校验共用同一份数据范围口径。
 */
public interface CmDepartmentService extends IService<CmDepartment> {

    List<DepartmentDetailRow> listInScope(DataScope scope, String keyword);

    /**
     * 单个分组，范围外或已删返回 null。调用方必须据此拒绝，不得当成「不存在」放行。
     */
    DepartmentDetailRow detailInScope(DataScope scope, long id);

    /**
     * 名称是否被占用。含逻辑删除行：库内 uk_name 覆盖全部行。
     */
    boolean existsName(String name, Long excludeId);

    long countChildren(long id);

    long countVehicles(long id);

    List<Long> descendantIds(long id);

    /**
     * 新增分组后把该分组授权给创建人，对齐旧系统 saveDepartment 后的授权行为。
     */
    void grantToUser(long userId, long departmentId);
}
