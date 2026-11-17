package com.cm.domain.alarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报警位状态机：开户、不重复开户、关户、多类型同时翻转、保留位过滤、ACC 反相。
 */
class AlarmStateMachineTest {

    private static final int OVER_SPEED_BIT = 1;

    private static final int EMERGENCY_BIT = 0;

    private static final int IN_OUT_AREA_BIT = 20;

    private static final int RESERVED_BIT = 15;

    private static final int ACC_BIT = 0;

    /** 状态字位 0 置位表示点火。 */
    private static final int IGNITION_ON = 1 << ACC_BIT;

    private static final Set<String> NO_OPEN = Set.of();

    @Test
    @DisplayName("位从 0 变 1 且库里无记录时开户")
    void should_open_alarm_when_bit_raises() {
        List<AlarmTransition> transitions = AlarmStateMachine.diff(
                flag(OVER_SPEED_BIT), IGNITION_ON, NO_OPEN);

        assertEquals(1, transitions.size());
        AlarmTransition transition = transitions.get(0);
        assertTrue(transition.opening());
        assertEquals("1", transition.alarmType());
        assertEquals(AlarmTransition.SOURCE_TERMINAL, transition.alarmSource());
        assertEquals("超速报警", transition.description());
    }

    @Test
    @DisplayName("位持续为 1 且已开户时不重复开户")
    void should_not_open_twice() {
        List<AlarmTransition> transitions = AlarmStateMachine.diff(
                flag(OVER_SPEED_BIT), IGNITION_ON, Set.of("1"));

        assertTrue(transitions.isEmpty());
    }

    @Test
    @DisplayName("位回到 0 且已开户时关户")
    void should_close_alarm_when_bit_clears() {
        List<AlarmTransition> transitions = AlarmStateMachine.diff(0, IGNITION_ON, Set.of("1"));

        assertEquals(1, transitions.size());
        assertFalse(transitions.get(0).opening());
        assertEquals("1", transitions.get(0).alarmType());
    }

    @Test
    @DisplayName("多个报警位同时置位时逐位开户")
    void should_open_each_bit() {
        int alarmFlag = flag(EMERGENCY_BIT) | flag(OVER_SPEED_BIT) | flag(IN_OUT_AREA_BIT);

        List<AlarmTransition> transitions = AlarmStateMachine.diff(alarmFlag, IGNITION_ON, NO_OPEN);

        assertEquals(3, transitions.size());
        assertTrue(transitions.stream().allMatch(AlarmTransition::opening));
        assertEquals(Set.of("0", "1", "20"),
                Set.copyOf(transitions.stream().map(AlarmTransition::alarmType).toList()));
    }

    @Test
    @DisplayName("保留位不生成报警")
    void should_ignore_reserved_bit() {
        assertTrue(AlarmStateMachine.diff(flag(RESERVED_BIT), IGNITION_ON, NO_OPEN).isEmpty());
    }

    @Test
    @DisplayName("熄火且库里无 Acc 记录时开户")
    void should_open_acc_alarm_when_ignition_off() {
        List<AlarmTransition> transitions = AlarmStateMachine.diff(0, 0, NO_OPEN);

        assertEquals(1, transitions.size());
        assertTrue(transitions.get(0).opening());
        assertEquals(AlarmTransition.ACC_TYPE, transitions.get(0).alarmType());
    }

    @Test
    @DisplayName("熄火持续上报且已开户时不重复开户")
    void should_not_open_acc_twice() {
        assertTrue(AlarmStateMachine.diff(0, 0, Set.of(AlarmTransition.ACC_TYPE)).isEmpty());
    }

    @Test
    @DisplayName("点火且库里 Acc 未结束时就关户")
    void should_close_acc_alarm_when_ignition_on() {
        List<AlarmTransition> transitions = AlarmStateMachine.diff(0, IGNITION_ON,
                Set.of(AlarmTransition.ACC_TYPE));

        assertEquals(1, transitions.size());
        assertFalse(transitions.get(0).opening());
    }

    @Test
    @DisplayName("首帧对齐：库里未结束但当前位已清时关户，不凭空多开 Acc")
    void should_close_stale_open_alarm_on_first_frame() {
        List<AlarmTransition> transitions = AlarmStateMachine.diff(0, IGNITION_ON, Set.of("1", "20"));

        assertEquals(2, transitions.size());
        assertTrue(transitions.stream().noneMatch(AlarmTransition::opening));
    }

    private static int flag(int bit) {
        return 1 << bit;
    }
}
