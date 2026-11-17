package com.cm.domain.position;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionVersionTest {

    private static final Instant DEVICE_TIME = Instant.parse("2026-11-14T09:30:15Z");

    @Test
    void should_rank_newer_device_time_first_when_device_times_differ() {
        PositionVersion older = new PositionVersion(DEVICE_TIME, DEVICE_TIME, "a");
        PositionVersion newer = new PositionVersion(DEVICE_TIME.plusSeconds(30), DEVICE_TIME, "a");

        assertTrue(newer.compareTo(older) > 0);
    }

    @Test
    void should_break_tie_by_ingest_time_then_event_id_when_device_time_equals() {
        PositionVersion base = new PositionVersion(DEVICE_TIME, DEVICE_TIME, "a");
        PositionVersion laterIngest = new PositionVersion(DEVICE_TIME, DEVICE_TIME.plusMillis(1), "a");
        PositionVersion largerEventId = new PositionVersion(DEVICE_TIME, DEVICE_TIME, "b");

        assertTrue(laterIngest.compareTo(base) > 0);
        assertTrue(largerEventId.compareTo(base) > 0);
        assertEquals(0, base.compareTo(new PositionVersion(DEVICE_TIME, DEVICE_TIME, "a")));
    }
}
