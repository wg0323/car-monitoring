package com.cm.admin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResultTest {

    @Test
    void should_return_success_code_when_ok() {
        ApiResult<String> result = ApiResult.ok("p1");

        assertEquals(0, result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals("p1", result.getData());
    }

    @Test
    void should_hide_internal_detail_when_unexpected_error() {
        ApiResult<Void> result = ApiResult.internalError();

        assertEquals(500, result.getCode());
        assertEquals("系统内部错误", result.getMessage());
        assertNull(result.getData());
    }
}
