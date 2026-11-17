package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警记录，对应 cm_alarm_record；合并旧表 alarmrecord / hisalarmrecord / newalarm。
 */
@Data
@TableName("cm_alarm_record")
public class CmAlarmRecord {

    private Long id;

    private String eventKey;

    private String alarmChannel;

    private Long companyId;

    private Long departmentId;

    private Long vehicleId;

    private String plateNo;

    private String simNo;

    private String alarmSource;

    private String alarmType;

    private String driver;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal latitude1;

    private BigDecimal longitude1;

    private String location;

    private String location1;

    private BigDecimal mileage1;

    private BigDecimal mileage2;

    private BigDecimal gas1;

    private BigDecimal gas2;

    private BigDecimal velocity;

    private BigDecimal timeSpan;

    private Long station;

    private String alarmStatus;

    private String description;

    private Integer ackSn;

    @TableField("is_processed")
    private Boolean processed;

    private LocalDateTime processedTime;

    private Long processedUserId;

    private String processedUserName;

    private String videoFileName;

    private String remark;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
