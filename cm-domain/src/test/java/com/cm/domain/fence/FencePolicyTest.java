package com.cm.domain.fence;

import com.cm.domain.exception.CmBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 区域保存规则：名称、报警类型、时间窗、限速、延时与备注。
 */
class FencePolicyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 17, 8, 0);

    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 17, 18, 0);

    private static final Long DEPARTMENT_ID = 1L;

    @Test
    @DisplayName("合法圆形命令返回解析后的几何")
    void should_accept_valid_command() {
        FenceGeometry geometry = FencePolicy.validate(command("大连港", 500, true, false, false, true,
                false, null, null, false, null, 30, "测试"));

        assertEquals(FenceAreaType.CIRCLE, geometry.getAreaType());
        assertEquals(500, geometry.getRadiusMeters());
    }

    @Test
    @DisplayName("名称为空或超长被拒绝")
    void should_reject_invalid_name() {
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("  ", 500,
                true, false, false, false, false, null, null, false, null, 0, "")));
        String longName = "区".repeat(FencePolicy.MAX_NAME_LENGTH + 1);
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command(longName, 500,
                true, false, false, false, false, null, null, false, null, 0, "")));
    }

    @Test
    @DisplayName("四个报警复选框全空时拒绝保存")
    void should_reject_command_without_alarm_type() {
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("无报警", 500,
                false, false, false, false, false, null, null, false, null, 0, "")));
    }

    @Test
    @DisplayName("启用时间段时起止时间必填且结束晚于开始")
    void should_validate_time_window() {
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("缺时间", 500,
                true, false, false, false, true, null, null, false, null, 0, "")));
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("时间倒挂", 500,
                true, false, false, false, true, END, START, false, null, 0, "")));
        FenceGeometry geometry = FencePolicy.validate(command("时间正常", 500,
                true, false, false, false, true, START, END, false, null, 0, ""));
        assertEquals(FenceAreaType.CIRCLE, geometry.getAreaType());
    }

    @Test
    @DisplayName("启用限速时限速值必须大于 0 且不超过上限")
    void should_validate_limit_speed() {
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("缺限速", 500,
                true, false, false, false, false, null, null, true, null, 0, "")));
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("限速为零", 500,
                true, false, false, false, false, null, null, true, BigDecimal.ZERO, 0, "")));
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("限速过高", 500,
                true, false, false, false, false, null, null, true,
                FencePolicy.MAX_SPEED_KPH.add(BigDecimal.ONE), 0, "")));
    }

    @Test
    @DisplayName("延时报警超出 0~3600 秒被拒绝")
    void should_validate_delay_seconds() {
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("延时负数", 500,
                true, false, false, false, false, null, null, false, null, -1, "")));
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("延时过大", 500,
                true, false, false, false, false, null, null, false, null,
                FencePolicy.MAX_DELAY_SECONDS + 1, "")));
    }

    @Test
    @DisplayName("未选择所属部门时拒绝保存")
    void should_reject_missing_department() {
        FenceCommand command = new FenceCommand("无部门", null, "circle", "121.500000,31.200000", 500,
                true, false, false, false, false, null, null, false, null, 0, "");

        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command));
    }

    @Test
    @DisplayName("备注超过 255 字符被拒绝")
    void should_reject_too_long_remark() {
        assertThrows(CmBusinessException.class, () -> FencePolicy.validate(command("备注超长", 500,
                true, false, false, false, false, null, null, false, null, 0,
                "备".repeat(FencePolicy.MAX_REMARK_LENGTH + 1))));
    }

    @Test
    @DisplayName("名称与备注归一化时去首尾空格，空备注落空串")
    void should_normalize_text_fields() {
        assertEquals("大连港", FencePolicy.normalizeName("  大连港  "));
        assertEquals("", FencePolicy.normalizeRemark(null));
        assertEquals("备注", FencePolicy.normalizeRemark("  备注 "));
    }

    private FenceCommand command(String name, Integer radius,
                                 boolean enterDriver, boolean enterPlatform, boolean exitDriver, boolean exitPlatform,
                                 boolean byTime, LocalDateTime start, LocalDateTime end,
                                 boolean limitSpeed, BigDecimal maxSpeed, Integer delaySeconds, String remark) {
        return new FenceCommand(name, DEPARTMENT_ID, "circle", "121.500000,31.200000", radius,
                enterDriver, enterPlatform, exitDriver, exitPlatform,
                byTime, start, end, limitSpeed, maxSpeed, delaySeconds, remark);
    }
}
