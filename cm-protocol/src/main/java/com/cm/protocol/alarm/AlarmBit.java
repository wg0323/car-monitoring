package com.cm.protocol.alarm;

import java.util.Optional;

/**
 * JT/T 808-2011 表 18 的报警标志位定义。位序号直接作为 cm_alarm_record.alarm_type 的取值，
 * 与旧库对齐：2026-11-15 只读核对 gps.alarmrecord 的 3210 行，alarm_type 实测就是 "0".."29" 这样的位序号字符串，
 * 仅位 20 用了别名 InArea、状态字位 0 用了 Acc / AccOn / AccOff。新库一律用位序号，
 * 与旧库对照时按别名映射，不再引入第二套编码。
 */
public enum AlarmBit {

    /** 位 0：紧急报警。 */
    EMERGENCY(0, "紧急报警"),

    /** 位 1：超速报警。 */
    OVER_SPEED(1, "超速报警"),

    /** 位 2：疲劳驾驶报警。 */
    FATIGUE(2, "疲劳驾驶报警"),

    /** 位 3：危险预警。 */
    DANGER_WARNING(3, "危险预警"),

    /** 位 4：GNSS 模块发生故障。 */
    GNSS_FAULT(4, "GNSS 模块故障"),

    /** 位 5：GNSS 天线未接或被剪断。 */
    GNSS_ANTENNA_OPEN(5, "GNSS 天线未接或被剪断"),

    /** 位 6：GNSS 天线短路。 */
    GNSS_ANTENNA_SHORT(6, "GNSS 天线短路"),

    /** 位 7：终端主电源欠压。 */
    MAIN_POWER_UNDERVOLTAGE(7, "终端主电源欠压"),

    /** 位 8：终端主电源掉电。 */
    MAIN_POWER_LOST(8, "终端主电源掉电"),

    /** 位 9：终端 LCD 或显示器故障。 */
    DISPLAY_FAULT(9, "终端 LCD 或显示器故障"),

    /** 位 10：TTS 模块故障。 */
    TTS_FAULT(10, "TTS 模块故障"),

    /** 位 11：摄像头故障。 */
    CAMERA_FAULT(11, "摄像头故障"),

    /** 位 12：道路运输证 IC 卡模块故障。 */
    IC_CARD_FAULT(12, "道路运输证 IC 卡模块故障"),

    /** 位 13：超速预警。 */
    OVER_SPEED_WARNING(13, "超速预警"),

    /** 位 14：疲劳驾驶预警。 */
    FATIGUE_WARNING(14, "疲劳驾驶预警"),

    /** 位 15：保留。 */
    RESERVED_15(15, "保留"),

    /** 位 16：保留。 */
    RESERVED_16(16, "保留"),

    /** 位 17：保留。 */
    RESERVED_17(17, "保留"),

    /** 位 18：当天累计驾驶超时。 */
    DRIVING_TIMEOUT(18, "当天累计驾驶超时"),

    /** 位 19：超时停车。 */
    PARKING_TIMEOUT(19, "超时停车"),

    /** 位 20：进出区域。旧库写成别名 InArea。 */
    IN_OUT_AREA(20, "进出区域"),

    /** 位 21：进出路线。旧库未出现该取值。 */
    IN_OUT_ROUTE(21, "进出路线"),

    /** 位 22：路段行驶时间不足或过长。 */
    ROUTE_TIME_ABNORMAL(22, "路段行驶时间不足或过长"),

    /** 位 23：路线偏离报警。 */
    ROUTE_DEVIATION(23, "路线偏离报警"),

    /** 位 24：车辆 VSS 故障。 */
    VSS_FAULT(24, "车辆 VSS 故障"),

    /** 位 25：车辆油量异常。 */
    OIL_ABNORMAL(25, "车辆油量异常"),

    /** 位 26：车辆被盗。 */
    VEHICLE_STOLEN(26, "车辆被盗"),

    /** 位 27：车辆非法点火。 */
    ILLEGAL_IGNITION(27, "车辆非法点火"),

    /** 位 28：车辆非法位移。 */
    ILLEGAL_MOVEMENT(28, "车辆非法位移"),

    /** 位 29：碰撞侧翻报警。 */
    CRASH_ROLLOVER(29, "碰撞侧翻报警"),

    /** 位 30：保留。 */
    RESERVED_30(30, "保留"),

    /** 位 31：保留。 */
    RESERVED_31(31, "保留");

    public static final int BIT_COUNT = 32;

    private static final AlarmBit[] BY_BIT = new AlarmBit[BIT_COUNT];

    static {
        for (AlarmBit item : values()) {
            BY_BIT[item.bit] = item;
        }
    }

    private final int bit;

    private final String description;

    AlarmBit(int bit, String description) {
        this.bit = bit;
        this.description = description;
    }

    public int bit() {
        return bit;
    }

    /**
     * 位序号文本，写入 alarm_type 的取值。
     */
    public String code() {
        return Integer.toString(bit);
    }

    public String description() {
        return description;
    }

    public static Optional<AlarmBit> of(int bit) {
        if (bit < 0 || bit >= BIT_COUNT) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_BIT[bit]);
    }

    /**
     * 未定义的位返回空串，调用方据此判断是否需要落库。
     */
    public static String descriptionOf(int bit) {
        return of(bit).map(AlarmBit::description).orElse("");
    }
}
