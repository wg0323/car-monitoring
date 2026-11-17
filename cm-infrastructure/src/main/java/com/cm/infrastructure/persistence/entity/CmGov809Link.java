package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 809 链路状态与检查点，对应 cm_gov809_link；每平台每角色一行。
 * 检查点由定时刷盘写入，不逐条更新：单链路每秒千条位置时逐条写会把 DB 打成瓶颈。
 */
@Data
@TableName("cm_gov809_link")
public class CmGov809Link {

    private Long id;

    private Long platformId;

    private String linkRole;

    private String mainLinkState;

    private String subLinkState;

    private LocalDateTime mainLinkAt;

    private LocalDateTime subLinkAt;

    private Long nextSeq;

    private Long lastSentSeq;

    private Long lastAckSeq;

    private Long pendingCount;

    private String lastError;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
