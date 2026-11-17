package com.cm.admin.exception;

import com.cm.admin.common.ApiResult;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.exception.CmConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int DOMAIN_BUSINESS_CODE = 400;

    private static final int UNAUTHORIZED_CODE = 401;

    private static final int FORBIDDEN_CODE = 403;

    private static final int CONFLICT_CODE = 409;

    private static final String BAD_CREDENTIALS_MESSAGE = "账号或口令错误";

    private static final String FORBIDDEN_MESSAGE = "没有该操作权限";

    /**
     * 登录失败统一返回 401 与同一文案，不区分「账号不存在」与「口令错误」，避免账号枚举。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("认证失败, message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.fail(UNAUTHORIZED_CODE, BAD_CREDENTIALS_MESSAGE));
    }

    /**
     * 方法级 @PreAuthorize 与业务层的越权判断都发生在 DispatcherServlet 内，
     * 必须在这里显式转 403，否则会被下面的 Exception 分支吞成 500。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("越权请求被拒绝, message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.fail(FORBIDDEN_CODE, FORBIDDEN_MESSAGE));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        log.warn("参数校验失败, message={}", message);
        return ResponseEntity.badRequest().body(ApiResult.fail(DOMAIN_BUSINESS_CODE, message));
    }

    /**
     * 查询参数类型不对（如 page=abc）是客户端错误。不单独处理会落到 Exception 分支，
     * 变成 500 并把正常的坏请求写成 ERROR 堆栈。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = ex.getName() + " 参数格式不合法";
        log.warn("参数类型不匹配, message={}", message);
        return ResponseEntity.badRequest().body(ApiResult.fail(DOMAIN_BUSINESS_CODE, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体无法解析, message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.fail(DOMAIN_BUSINESS_CODE, "请求体格式不合法"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常，code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(CmBusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleDomainBusinessException(CmBusinessException ex) {
        log.warn("领域业务异常，message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.fail(DOMAIN_BUSINESS_CODE, ex.getMessage()));
    }

    /**
     * 资源当前状态不允许该操作（如向离线终端下发即时指令）。用 409 与参数错误区分，
     * 前端才能给出「先让终端上线」这类可操作的提示。
     */
    @ExceptionHandler(CmConflictException.class)
    public ResponseEntity<ApiResult<Void>> handleConflictException(CmConflictException ex) {
        log.warn("资源状态冲突, message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResult.fail(CONFLICT_CODE, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResult.internalError());
    }
}
