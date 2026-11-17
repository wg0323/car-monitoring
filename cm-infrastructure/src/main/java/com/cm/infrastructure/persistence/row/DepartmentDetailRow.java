package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆分组（部门）管理行。比下拉项多了旧 editDep 表单的全部字段与上级名称。
 */
@Data
public class DepartmentDetailRow {

    private Long id;

    private Long parentId;

    /** 上级车组名称，根部门为 null。 */
    private String parentName;

    private Long companyId;

    private String name;

    private String departmentType;

    private String memberNo;

    private String roadPermitWord;

    private String roadPermitNo;

    private String businessScope;

    private String region;

    private String contactPerson;

    private String contactPhone;

    private String remark;

    private LocalDateTime createTime;
}
