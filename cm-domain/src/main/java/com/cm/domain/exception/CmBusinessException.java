package com.cm.domain.exception;

/**
 * 领域层业务异常。放在 cm-domain 保持纯 Java，使 cm-infrastructure 与 cm-admin-api 能共用同一异常类型。
 */
public class CmBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CmBusinessException(String message) {
        super(message);
    }

    public CmBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
