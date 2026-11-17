package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 809 接入平台配置，对应 cm_platform_info；旧表 platforminfo。
 * upstream=1 是本平台作为下级连出的上级平台，=0 是本平台作为上级接入的下级平台。
 */
@Data
@TableName("cm_platform_info")
public class CmPlatformInfo {

    private Long id;

    private Long platformId;

    private String name;

    private String userId;

    private String password;

    private String sublinkServerIp;

    private Integer sublinkPort;

    private Integer upstream;

    private Integer isEnabled;

    private String mainLinkState;

    private String subLinkState;

    private Integer checkInterval;

    private String checkStartTime;

    private String checkEndTime;

    private String checkQuestion;

    private Long companyId;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
