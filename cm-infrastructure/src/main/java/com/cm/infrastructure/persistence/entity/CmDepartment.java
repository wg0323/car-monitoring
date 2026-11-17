package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门树，对应 cm_department；旧表 department。
 */
@Data
@TableName("cm_department")
public class CmDepartment {

    private Long id;

    private Long parentId;

    private Long companyId;

    private String name;

    private String contactPerson;

    private String contactPhone;

    private String businessScope;

    private String region;

    private String roadPermitNo;

    private String roadPermitWord;

    private String memberNo;

    private String departmentType;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
