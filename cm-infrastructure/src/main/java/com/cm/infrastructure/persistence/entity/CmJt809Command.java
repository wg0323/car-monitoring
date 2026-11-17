package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 809 指令与报文日志，对应 cm_jt809_command；旧表 jt809command。
 * 旧系统用它做“每秒轮询下发”的命令队列，新系统只当审计与页面数据，下发不再轮询。
 */
@Data
@TableName("cm_jt809_command")
public class CmJt809Command {

    private Long id;

    private Long platformId;

    private Integer sn;

    private Integer cmd;

    private Integer subCmd;

    private String cmdData;

    private String descr;

    private String plateNo;

    private Integer plateColor;

    private String simNo;

    private String source;

    private String commandStatus;

    private Long userId;

    private Long companyId;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
