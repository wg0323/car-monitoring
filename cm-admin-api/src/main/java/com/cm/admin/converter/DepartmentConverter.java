package com.cm.admin.converter;

import com.cm.admin.vo.DepartmentDetailVO;
import com.cm.admin.vo.DepartmentSaveRequest;
import com.cm.infrastructure.persistence.entity.CmDepartment;
import com.cm.infrastructure.persistence.row.DepartmentDetailRow;

/**
 * 车辆分组管理页的转换。请求体到实体存在空串默认值与名称去空格，属于非等价映射，集中在这里。
 */
public final class DepartmentConverter {

    private static final long ROOT_PARENT_ID = 0L;

    private DepartmentConverter() {
    }

    /**
     * 把请求体写到实体上。companyId 与删除标记由调用方按上下文设置。
     */
    public static void apply(DepartmentSaveRequest request, CmDepartment entity) {
        entity.setName(text(request.getName()));
        entity.setParentId(request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId());
        entity.setDepartmentType(text(request.getDepartmentType()));
        entity.setMemberNo(text(request.getMemberNo()));
        entity.setRoadPermitWord(text(request.getRoadPermitWord()));
        entity.setRoadPermitNo(text(request.getRoadPermitNo()));
        entity.setBusinessScope(text(request.getBusinessScope()));
        entity.setRegion(text(request.getRegion()));
        entity.setContactPerson(text(request.getContactPerson()));
        entity.setContactPhone(text(request.getContactPhone()));
        entity.setRemark(text(request.getRemark()));
    }

    public static DepartmentDetailVO toVO(DepartmentDetailRow row) {
        DepartmentDetailVO vo = new DepartmentDetailVO();
        vo.setId(row.getId());
        vo.setParentId(row.getParentId());
        vo.setParentName(row.getParentName());
        vo.setName(row.getName());
        vo.setDepartmentType(row.getDepartmentType());
        vo.setMemberNo(row.getMemberNo());
        vo.setRoadPermitWord(row.getRoadPermitWord());
        vo.setRoadPermitNo(row.getRoadPermitNo());
        vo.setBusinessScope(row.getBusinessScope());
        vo.setRegion(row.getRegion());
        vo.setContactPerson(row.getContactPerson());
        vo.setContactPhone(row.getContactPhone());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /** 库列 NOT NULL DEFAULT ''，请求里的 null 统一落为空串。 */
    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
