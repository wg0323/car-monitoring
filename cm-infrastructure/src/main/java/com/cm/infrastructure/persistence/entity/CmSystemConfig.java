package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置，对应 cm_system_config；旧表 systemconfig 的 24 列改为键值行。
 */
@Data
@TableName("cm_system_config")
public class CmSystemConfig {

    private Long id;

    private Long companyId;

    private String configKey;

    private String configValue;

    private String description;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
