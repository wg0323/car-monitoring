package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平台用户，对应 cm_user；旧表 userinfo。旧库明文 password 不迁移。
 */
@Data
@TableName("cm_user")
public class CmUser {

    private Long id;

    private Long companyId;

    private String username;

    private String displayName;

    private String passwordHash;

    private String userType;

    private String userState;

    private String phoneNo;

    private BigDecimal mapCenterLat;

    private BigDecimal mapCenterLng;

    private Integer mapLevel;

    private String mapType;

    private LocalDateTime lastLoginTime;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
