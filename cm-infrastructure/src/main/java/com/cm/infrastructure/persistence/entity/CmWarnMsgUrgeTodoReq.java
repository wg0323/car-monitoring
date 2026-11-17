package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 809 报警督办请求与应答，对应 cm_warn_msg_urge_todo_req；旧表 warnmsgurgtodoreq。
 * （platform_id, supervise_id）唯一：上级重发同一督办不会重复落库，应答也只推一次。
 */
@Data
@TableName("cm_warn_msg_urge_todo_req")
public class CmWarnMsgUrgeTodoReq {

    private Long id;

    private Long platformId;

    private Long superviseId;

    private String plateNo;

    private Integer plateColor;

    private Integer warnSrc;

    private Integer warnType;

    private LocalDateTime warnTime;

    private LocalDateTime supervisionEndTime;

    private Integer supervisionLevel;

    private String supervisor;

    private String supervisorTel;

    private String supervisorEmail;

    private Long vehicleId;

    private Integer ackResult;

    private LocalDateTime ackTime;

    private Long companyId;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
