package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 终端基础资料，对应 cm_terminal；旧表 terminal。证书凭据不入库。
 */
@Data
@TableName("cm_terminal")
public class CmTerminal {

    private Long id;

    private Long companyId;

    private Long departmentId;

    private String termNo;

    private String devNo;

    private String simNo;

    private String seqNo;

    private String termType;

    private String terminalState;

    @TableField("is_bound")
    private Boolean bound;

    private String imei;

    private String modelNo;

    private String termSn;

    private String vender;

    private String makeFactory;

    private String makeNo;

    private LocalDateTime makeTime;

    private String installAddress;

    private LocalDateTime installTime;

    private String verHardware;

    private String verSoftware;

    private String verProtocol;

    private String unitNo;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
