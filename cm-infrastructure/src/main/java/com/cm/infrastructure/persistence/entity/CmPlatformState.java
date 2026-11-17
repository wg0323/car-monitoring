package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 809 链路状态快照，对应 cm_platform_state；旧表 platformstate。
 * 旧表是单行全局快照，新表按平台一行：多上级链路时图标不再互相覆盖。
 */
@Data
@TableName("cm_platform_state")
public class CmPlatformState {

    private Long id;

    private Long platformId;

    private LocalDateTime gpsServerDate;

    private String gpsServerState;

    private LocalDateTime mainLinkDate;

    private String mainLinkState;

    private LocalDateTime subLinkDate;

    private String subLinkState;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
