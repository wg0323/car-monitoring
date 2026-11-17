package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "报表页筛选项集合")
public class ReportOptionsVO {

    @Schema(description = "报警类型选项", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReportOptionVO> alarmTypes;

    @Schema(description = "报警来源选项", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReportOptionVO> alarmSources;

    @Schema(description = "车牌颜色选项", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReportOptionVO> plateColors;

    @Schema(description = "上下线记录类型选项", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReportOptionVO> onlineRecordTypes;

    @Schema(description = "在线状态选项", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReportOptionVO> onlineStatuses;
}
