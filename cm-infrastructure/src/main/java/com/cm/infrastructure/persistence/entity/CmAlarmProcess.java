package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报警处理审计，对应 cm_alarm_process；与报警状态更新处在同一事务。
 */
@Data
@TableName("cm_alarm_process")
public class CmAlarmProcess {

    private Long id;

    private Long alarmId;

    private Long processorId;

    private String processorName;

    private String processResult;

    private String processRemark;

    private LocalDateTime processTime;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
