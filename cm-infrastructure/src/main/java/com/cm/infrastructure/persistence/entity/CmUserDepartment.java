package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户可访问部门，对应 cm_user_department；旧表 userdepartment。
 */
@Data
@TableName("cm_user_department")
public class CmUserDepartment {

    private Long id;

    private Long userId;

    private Long departmentId;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
