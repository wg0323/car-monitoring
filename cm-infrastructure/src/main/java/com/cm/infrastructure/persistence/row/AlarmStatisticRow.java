package com.cm.infrastructure.persistence.row;

import lombok.Data;

/**
 * 报警类型统计结果行。
 */
@Data
public class AlarmStatisticRow {

    private String alarmType;

    private Long alarmCount;

    private Long processedCount;
}
