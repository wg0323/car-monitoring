package com.cm.infrastructure.gov809;

/**
 * 809 链路状态与检查点。状态变更立即落库并同步 cm_platform_state 快照，
 * 检查点由调用方定时刷盘，避免每秒千条位置逐条写库。
 */
public interface Gov809LinkService {

    String ROLE_ENTERPRISE_UPSTREAM = "ENTERPRISE_UPSTREAM";

    String ROLE_GOV_DOWNSTREAM = "GOV_DOWNSTREAM";

    String STATE_DISCONNECTED = "DISCONNECTED";

    String STATE_CONNECTED = "CONNECTED";

    String STATE_LOGGED_IN = "LOGGED_IN";

    Gov809LinkSnapshot load(long platformId, String role);

    void markMainLink(long platformId, String role, String state, String error);

    void markSubLink(long platformId, String role, String state, String error);

    void flushCheckpoint(long platformId, String role, long maxAllocatedSeq, long lastSentSeq,
                         long pendingCount);

    /** 重启后续号用：库里记录的最大已分配序号。 */
    long maxAllocatedSeq(long platformId, String role);
}
 
