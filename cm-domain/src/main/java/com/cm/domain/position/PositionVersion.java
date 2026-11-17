package com.cm.domain.position;

import java.time.Instant;
import java.util.Objects;

/**
 * 最新位置版本号，按 (设备有效时间, 接入时间, 事件 ID) 字典序比较。
 * deviceSeq 不参与全时间范围排序：它可能回绕，也可能在设备重启后归零。
 */
public record PositionVersion(Instant effectiveDeviceTime, Instant ingestTime, String eventId)
        implements Comparable<PositionVersion> {

    public PositionVersion {
        Objects.requireNonNull(effectiveDeviceTime, "effectiveDeviceTime");
        Objects.requireNonNull(ingestTime, "ingestTime");
        Objects.requireNonNull(eventId, "eventId");
    }

    @Override
    public int compareTo(PositionVersion other) {
        Objects.requireNonNull(other, "other");
        int byDeviceTime = effectiveDeviceTime.compareTo(other.effectiveDeviceTime);
        if (byDeviceTime != 0) {
            return byDeviceTime;
        }
        int byIngestTime = ingestTime.compareTo(other.ingestTime);
        if (byIngestTime != 0) {
            return byIngestTime;
        }
        return eventId.compareTo(other.eventId);
    }
}
