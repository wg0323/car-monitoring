package com.cm.domain.fence;

import com.cm.domain.exception.CmBusinessException;

/**
 * 区域几何类型。旧系统 areaType 为 circle / rect / polygon 三值，本项目不新增第四种。
 */
public enum FenceAreaType {

    CIRCLE("circle"),

    RECT("rect"),

    POLYGON("polygon");

    private final String code;

    FenceAreaType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static FenceAreaType fromCode(String code) {
        for (FenceAreaType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new CmBusinessException("不支持的区域类型：" + code);
    }
}
