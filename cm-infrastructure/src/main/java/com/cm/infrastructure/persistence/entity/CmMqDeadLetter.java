package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MQ 死信持久化记录，对应 cm_mq_dead_letter。
 * 队列只声明 DLQ 会丢证据，这里保留原始消息体与路由，重放时原样投递并复用同一业务幂等键。
 */
@Data
@TableName("cm_mq_dead_letter")
public class CmMqDeadLetter {

    private Long id;

    private String messageId;

    private String sourceQueue;

    private String exchangeName;

    private String routingKey;

    private String commandId;

    private String payload;

    private String headersJson;

    private String reason;

    private String deadStatus;

    private Integer replayCount;

    private LocalDateTime replayedAt;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
