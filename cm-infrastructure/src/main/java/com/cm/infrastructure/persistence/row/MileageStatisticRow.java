package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 里程与油耗统计结果行，对应旧 report.selectMileageStatic 系列。
 */
@Data
public class MileageStatisticRow {

    private LocalDateTime statisticDate;

    private String plateNo;

    private String departmentName;

    private BigDecimal mileage;

    private BigDecimal oil;

    private BigDecimal workHour;
}
