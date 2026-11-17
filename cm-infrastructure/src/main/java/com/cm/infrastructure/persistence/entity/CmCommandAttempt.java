package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指令下行尝试，对应 cm_command_attempt。
 * 网关先落这条映射再 writeAndFlush；终端 ACK 只能按 (sim_no, terminal_serial_no, connection_incarnation) 回查，
 * 不允许按“最近一条指令”猜测归属。
 */
@Data
@TableName("cm_command_attempt")
public class CmCommandAttempt {

    private Long id;

    private String commandId;

    private Integer attemptNo;

    private String simNo;

    private Integer terminalSerialNo;

    private Long connectionIncarnation;

    private String gatewayId;

    private Integer downlinkMessageId;

    private Integer expectedAckMessageId;

    private String writeStatus;

    private LocalDateTime writeAt;

    private String errorReason;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
