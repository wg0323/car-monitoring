package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表与用户详情的查询结果行，属持久层行对象，不是 API 响应 VO。
 */
@Data
public class UserRow {

    private Long id;

    private String username;

    private String displayName;

    private String userType;

    private String phoneNo;

    /** 由 user_state 折算：非 disabled 即启用，与登录、数据范围的判定同口径。 */
    private Boolean enabled;

    private LocalDateTime createTime;
}