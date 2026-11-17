package com.cm.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "统一响应体")
public class ApiResult<T> {

    private static final int SUCCESS_CODE = 0;

    private static final int INTERNAL_ERROR_CODE = 500;

    @Schema(description = "业务状态码：0 表示成功，非 0 表示失败", example = "0")
    private int code;

    @Schema(description = "提示信息", example = "ok")
    private String message;

    @Schema(description = "响应数据；失败时为 null")
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage("ok");
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> ApiResult<T> internalError() {
        return fail(INTERNAL_ERROR_CODE, "系统内部错误");
    }
}
