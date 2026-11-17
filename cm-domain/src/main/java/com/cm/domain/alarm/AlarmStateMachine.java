package com.cm.domain.alarm;

import com.cm.protocol.alarm.AlarmBit;
import com.cm.protocol.alarm.VehicleStatusBit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 终端报警位状态机。只做位运算与集合比对，不碰数据库、不碰 Redis，便于单独验证。
 *
 * <p>开 / 关只由「当前帧 + 库里已存在未结束记录」决定，不看上一帧：
 * 页面首帧、网关重启、多实例竞争消费时，库里的未结束记录才是唯一真相。
 *
 * <p>保留位（描述为「保留」）不生成报警，避免用无意义的类型污染报警列表。
 */
public final class AlarmStateMachine {

    private static final String RESERVED_DESCRIPTION = "保留";

    private static final int ACC_SHIFT = VehicleStatusBit.ACC.bit();

    private AlarmStateMachine() {
    }

    /**
     * 报警位的开与关完全由「当前帧 + 已存在未结束记录」决定：
     * 位为 1 且库里无记录则开户，位为 0 且库里有记录则关户；其余情况不动。
     * 这样既保证持续上报不重复开户，也让网关重启后的首帧能对齐库里的真相。
     *
     * @param currentAlarmFlag 当前帧报警标志位
     * @param currentStatus    当前帧状态字
     * @param openTypes        该 SIM 当前未结束报警的 alarm_type 集合，调用方只读
     */
    public static List<AlarmTransition> diff(int currentAlarmFlag, int currentStatus,
                                             Set<String> openTypes) {
        List<AlarmTransition> transitions = new ArrayList<>();
        diffAlarmBits(currentAlarmFlag, openTypes, transitions);
        diffAcc(currentStatus, openTypes, transitions);
        return transitions;
    }

    private static void diffAlarmBits(int currentAlarmFlag, Set<String> openTypes,
                                      List<AlarmTransition> transitions) {
        for (int bit = 0; bit < AlarmBit.BIT_COUNT; bit++) {
            AlarmBit definition = AlarmBit.of(bit).orElse(null);
            if (definition == null || RESERVED_DESCRIPTION.equals(definition.description())) {
                continue;
            }
            String code = definition.code();
            boolean open = isSet(currentAlarmFlag, bit);
            if (open && !openTypes.contains(code)) {
                transitions.add(AlarmTransition.open(code, definition.description()));
            } else if (!open && openTypes.contains(code)) {
                transitions.add(AlarmTransition.close(code, definition.description()));
            }
        }
    }

    /**
     * ACC 极性反转：位 1 为点火，位 0 为熄火；熄火算报警开始，点火算报警结束。
     * 同样只看「当前帧 + 库里是否已有未结束的 Acc」，不依赖进程内存里的上一帧状态：
     * 否则多实例竞争消费或网关重启后，熄火期间会漏开、或两帧分属不同实例时重复开户。
     */
    private static void diffAcc(int currentStatus, Set<String> openTypes,
                                List<AlarmTransition> transitions) {
        boolean ignitionOn = isSet(currentStatus, ACC_SHIFT);
        boolean accOpen = openTypes.contains(AlarmTransition.ACC_TYPE);
        if (ignitionOn && accOpen) {
            transitions.add(AlarmTransition.close(AlarmTransition.ACC_TYPE, AlarmTransition.ACC_DESCRIPTION));
        } else if (!ignitionOn && !accOpen) {
            transitions.add(AlarmTransition.open(AlarmTransition.ACC_TYPE, AlarmTransition.ACC_DESCRIPTION));
        }
    }

    private static boolean isSet(int flag, int bit) {
        return (flag & (1 << bit)) != 0;
    }
}
