package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 无法归属的终端应答，对应 cm_command_ack_exception。
 * 映射缺失或应答类型与下行消息不对应时只落这张表，不替终端猜归属。
 */
@Data
@TableName("cm_command_ack_exception")
public class CmCommandAckException {

    private Long id;

    private String simNo;

    private Integer terminalSerialNo;

    private Long connectionIncarnation;

    private String gatewayId;

    private Integer ackMessageId;

    private Integer ackResult;

    private String commandId;

    private String reason;

    private String rawHex;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
