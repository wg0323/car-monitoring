package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警明细分页查询结果行。
 */
@Data
public class AlarmRow {

    private Long id;

    private String eventKey;

    private String alarmChannel;

    private String plateNo;

    private Integer plateColor;

    private String simNo;

    private Long vehicleId;

    private String departmentName;

    private String alarmType;

    private String alarmSource;

    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal velocity;

    private BigDecimal timeSpan;

    private Boolean processed;

    private LocalDateTime processedTime;

    private String processedUserName;

    private String remark;
}
