package com.cm.domain.exception;

/**
 * 资源当前状态与请求冲突的业务异常，例如向不在线的终端下发即时指令。
 * 与 {@link CmBusinessException} 区分开，HTTP 层据此返回 409 而不是 400。
 */
public class CmConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CmConflictException(String message) {
        super(message);
    }
}
