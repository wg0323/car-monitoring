package com.cm.domain.alarm;

/**
 * 一次报警状态跳变。opening 为 true 表示报警开始（落新记录），false 表示报警结束（关旧记录）。
 * alarm_source 沿用旧系统的 terminal_alarm：2026-11-15 核对 gps.alarmrecord，终端报警 3171 行全部是这一个来源，
 * 旧代码里定义的 terminal_state 在库里没有一行，因此不引入这个取值。
 */
public record AlarmTransition(String alarmType, String alarmSource, String description, boolean opening) {

    public static final String SOURCE_TERMINAL = "terminal_alarm";

    /**
     * ACC 不是报警标志位，而是状态字位 0，旧库用 Acc / AccOn / AccOff 三个名字，统一用 Acc。
     */
    public static final String ACC_TYPE = "Acc";

    public static final String ACC_DESCRIPTION = "ACC 熄火";

    public static AlarmTransition open(String alarmType, String description) {
        return new AlarmTransition(alarmType, SOURCE_TERMINAL, description, true);
    }

    public static AlarmTransition close(String alarmType, String description) {
        return new AlarmTransition(alarmType, SOURCE_TERMINAL, description, false);
    }
}
