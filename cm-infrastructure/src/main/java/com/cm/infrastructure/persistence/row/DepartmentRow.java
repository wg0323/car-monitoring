package com.cm.infrastructure.persistence.row;

import lombok.Data;

/**
 * 部门树查询结果行。
 */
@Data
public class DepartmentRow {

    private Long id;

    private Long parentId;

    private String name;

    private Long companyId;
}
