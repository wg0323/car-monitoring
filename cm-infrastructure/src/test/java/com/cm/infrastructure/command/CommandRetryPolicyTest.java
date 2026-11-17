package com.cm.infrastructure.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 退避节奏：指数增长、封顶、首次尝试不为零等待。
 */
class CommandRetryPolicyTest {

    private static final long BASE_MILLIS = 1000L;

    private static final long MAX_MILLIS = 60000L;

    @Test
    @DisplayName("退避按 1s、2s、4s、8s 递增")
    void should_grow_exponentially() {
        assertEquals(1000L, CommandRetryPolicy.backoffMillis(1, BASE_MILLIS, MAX_MILLIS));
        assertEquals(2000L, CommandRetryPolicy.backoffMillis(2, BASE_MILLIS, MAX_MILLIS));
        assertEquals(4000L, CommandRetryPolicy.backoffMillis(3, BASE_MILLIS, MAX_MILLIS));
        assertEquals(8000L, CommandRetryPolicy.backoffMillis(4, BASE_MILLIS, MAX_MILLIS));
        assertEquals(16000L, CommandRetryPolicy.backoffMillis(5, BASE_MILLIS, MAX_MILLIS));
    }

    @Test
    @DisplayName("退避到上限后不再增长，也不会因移位溢出变成负数")
    void should_cap_at_max() {
        assertEquals(MAX_MILLIS, CommandRetryPolicy.backoffMillis(7, BASE_MILLIS, MAX_MILLIS));
        assertEquals(MAX_MILLIS, CommandRetryPolicy.backoffMillis(40, BASE_MILLIS, MAX_MILLIS));
        assertEquals(MAX_MILLIS, CommandRetryPolicy.backoffMillis(200, BASE_MILLIS, MAX_MILLIS));
    }

    @Test
    @DisplayName("非法尝试序号按首次尝试处理")
    void should_treat_non_positive_attempt_as_first() {
        assertEquals(1000L, CommandRetryPolicy.backoffMillis(0, BASE_MILLIS, MAX_MILLIS));
        assertEquals(1000L, CommandRetryPolicy.backoffMillis(-5, BASE_MILLIS, MAX_MILLIS));
    }
}
