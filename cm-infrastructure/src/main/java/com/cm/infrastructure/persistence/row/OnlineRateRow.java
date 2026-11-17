package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 部门与单车上线率统计的共用结果行，未使用的维度列为 null。
 */
@Data
public class OnlineRateRow {

    private LocalDateTime statisticDate;

    private String departmentName;

    private String plateNo;

    private Integer vehicleNum;

    private Integer onlineNum;

    private Integer offlineNum;

    private BigDecimal onlineRate;
}
