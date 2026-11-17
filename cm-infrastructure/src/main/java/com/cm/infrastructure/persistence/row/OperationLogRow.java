package com.cm.infrastructure.persistence.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志查询结果行，对应旧 report.selectOperationLog。
 */
@Data
public class OperationLogRow {

    private String userName;

    private String operationDescr;

    private String detail;

    private String ip;

    private String url;

    private LocalDateTime createTime;
}
