package com.cm.domain.registration;

/**
 * 终端注册判定结果。code 就是 0x8100 注册应答里的结果码，由网关在编码时直接取用。
 *
 * <p>不实现 1（车辆已注册）与 3（终端已注册）：旧实现靠进程内 map 判「已注册」，
 * 重启即失效，不是可靠的注册态事实源；新实现不引入新的注册态。
 */
public enum RegistrationVerdict {

    /** 注册成功，可以下发鉴权码。 */
    SUCCESS(0),

    /** 车辆（车牌 + 车牌颜色）不在允许接入的范围。 */
    VEHICLE_NOT_FOUND(2),

    /** 车辆存在，但终端号与 SIM 都对不上任何终端。 */
    TERMINAL_NOT_FOUND(4);

    private final int code;

    RegistrationVerdict(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
