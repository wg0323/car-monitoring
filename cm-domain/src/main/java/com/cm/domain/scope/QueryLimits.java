package com.cm.domain.scope;

/**
 * 查询边界常量，供多个 Service 共用，避免各写一份后改一处漏一处。
 */
public final class QueryLimits {

    /** 单页最大条数。 */
    public static final long MAX_PAGE_SIZE = 1000L;

    /** 页码上限，防止 (pageNo - 1) * pageSize 长整型溢出产生负数 offset。 */
    public static final long MAX_PAGE_NO = 1000000L;

    /** 单次报表查询允许的最大时间跨度（天）。 */
    public static final long MAX_RANGE_DAYS = 366L;

    /** 单次同步导出的最大行数，超过时提示缩小时间范围。 */
    public static final long MAX_EXPORT_ROWS = 50000L;

    /** 单次轨迹查询与导出的最大时间跨度（天），2026-11-16 裁决取 3 天。 */
    public static final long MAX_TRACK_RANGE_DAYS = 3L;

    /** 单次轨迹查询与导出的最大点数。页面回放要一次拿到全部点，超过上限只能缩小范围。 */
    public static final long MAX_TRACK_POINTS = 20000L;

    private QueryLimits() {
    }
}
