package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 终端指令及状态机，对应 cm_terminal_command；旧表 terminalcommand。
 */
@Data
@TableName("cm_terminal_command")
public class CmTerminalCommand {

    private Long id;

    private String commandId;

    private Long companyId;

    private Long departmentId;

    private Long vehicleId;

    private String plateNo;

    private String simNo;

    private Integer commandType;

    private String commandCode;

    private String commandData;

    private Integer terminalSerialNo;

    private Integer attemptNo;

    private String commandStatus;

    private LocalDateTime sentAt;

    private LocalDateTime ackAt;

    private LocalDateTime expireAt;

    private Long operatorId;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
