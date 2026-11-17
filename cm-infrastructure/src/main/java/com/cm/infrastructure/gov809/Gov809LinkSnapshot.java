package com.cm.infrastructure.gov809;

/**
 * 809 链路快照。位置类报文没有业务应答，lastAckSeq 与 lastSentSeq 同步推进；
 * 督办等有应答的报文才由 ack 侧单独推 lastAckSeq。
 */
public record Gov809LinkSnapshot(long platformId, String role, String mainLinkState,
                                 String subLinkState, long lastSentSeq, long lastAckSeq,
                                 long pendingCount, String lastError) {
}
 
