package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台间报文，对应 cm_gov_platform_msg；旧表 govplatformmsg。
 * 平台间消息（0x9300/0x9301）不在 P3-S3 范围，表先建好，写入留到该功能落地时。
 */
@Data
@TableName("cm_gov_platform_msg")
public class CmGovPlatformMsg {

    private Long id;

    private Long platformId;

    private String msgType;

    private Integer subType;

    private String plateNo;

    private Integer plateColor;

    private String msg;

    private String msgStatus;

    private Long companyId;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
