package com.cm.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 里程与油耗统计，对应 cm_mileage_statistic；旧表 mileagestatistic（实测 0 行）。
 * period_type 沿用旧口径：0 小时、1 天、2 月、3 时间段。
 */
@Data
@TableName("cm_mileage_statistic")
public class CmMileageStatistic {

    public static final int PERIOD_DAY = 1;

    private Long id;

    private Long companyId;

    private Long departmentId;

    private String departmentName;

    private String plateNo;

    private Integer periodType;

    private String periodDescr;

    private LocalDateTime statisticDate;

    private BigDecimal workHour;

    private BigDecimal mileage;

    private BigDecimal mileage1;

    private BigDecimal mileage2;

    private BigDecimal oil;

    private BigDecimal oil1;

    private BigDecimal oil2;

    @TableField("is_deleted")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
