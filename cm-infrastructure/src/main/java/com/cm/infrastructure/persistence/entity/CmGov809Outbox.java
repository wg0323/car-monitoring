package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 809 断链积压与检查点补发队列，对应 cm_gov809_outbox。
 * payload 存的是已编码的消息体十六进制：补发时原样编帧，不重新推导业务字段。
 */
@Data
@TableName("cm_gov809_outbox")
public class CmGov809Outbox {

    private Long id;

    private Long platformId;

    private Long eventSeq;

    private String bizEventId;

    private Integer msgType;

    private Integer subType;

    private String plateNo;

    private Integer plateColor;

    private String payload;

    private String outboxStatus;

    private Integer attempt;

    private String lastError;

    private LocalDateTime sentAt;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
 
