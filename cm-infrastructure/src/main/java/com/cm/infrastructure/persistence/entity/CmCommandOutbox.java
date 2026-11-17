package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指令事务性 Outbox，对应 cm_command_outbox。
 * 与指令记录同事务写入；relay、confirm 与死信处理在 P3 接入。
 */
@Data
@TableName("cm_command_outbox")
public class CmCommandOutbox {

    private Long id;

    private String commandId;

    private String exchangeName;

    private String routingKey;

    private String payload;

    private String publishStatus;

    private LocalDateTime confirmAt;

    private Integer retryCount;

    private String leaseOwner;

    private LocalDateTime leaseExpireAt;

    private LocalDateTime nextRetryAt;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
