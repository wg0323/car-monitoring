package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量处理结果：成功条数与被跳过的明细。单条处理失败直接由全局异常处理返回错误，
 * 批量才用「部分成功」语义，与旧系统一次提交多条的行为对齐。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量处理结果：成功条数与跳过明细")
public class AlarmProcessResultVO {

    @Schema(description = "成功处理的条数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private int processed;

    @Schema(description = "被跳过的报警，无跳过时为空数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AlarmSkipVO> skipped;
}
