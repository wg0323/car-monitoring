package com.cm.infrastructure.persistence.row;

import lombok.Data;

/**
 * 用户所属角色的查询结果行。
 */
@Data
public class UserRoleRow {

    private Long roleId;

    private String roleCode;

    private String roleName;
}
