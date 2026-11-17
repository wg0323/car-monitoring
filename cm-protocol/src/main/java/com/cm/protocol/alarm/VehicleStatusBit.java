package com.cm.protocol.alarm;

/**
 * JT/T 808-2011 表 19 状态位的位定义。本轮只建模 Acc，其余状态位留给需要它们的业务；
 * 旧系统把状态位 0 当作 ACC 并反转极性：位 1 表示点火（结束 Acc 记录），位 0 表示熄火（开启 Acc 记录）。
 */
public enum VehicleStatusBit {

    /** 位 0：ACC 状态。 */
    ACC(0, "ACC 状态");

    private final int bit;

    private final String description;

    VehicleStatusBit(int bit, String description) {
        this.bit = bit;
        this.description = description;
    }

    public int bit() {
        return bit;
    }

    public String description() {
        return description;
    }

    /**
     * 状态字中该位的原始取值，true 表示位为 1。
     */
    public boolean isSet(int status) {
        return (status & (1 << bit)) != 0;
    }
}
