package com.cm.admin.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@ExcelIgnoreUnannotated
@Data
@Schema(description = "报警查询报表行")
public class ReportAlarmVO {

    @ExcelProperty(value = "车牌号", index = 0)
    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @ExcelProperty(value = "颜色", index = 1)
    @Schema(description = "车牌颜色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "蓝色")
    private String plateColorName;

    @ExcelProperty(value = "报警类型", index = 2)
    @Schema(description = "报警类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "超速报警")
    private String alarmTypeName;

    @ExcelProperty(value = "报警来源", index = 3)
    @Schema(description = "报警来源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "终端报警")
    private String alarmSourceName;

    @ExcelProperty(value = "报警地点", index = 4)
    @Schema(description = "报警地点描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String location;

    @ExcelProperty(value = "开始时间", index = 5)
    @Schema(description = "报警开始时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startTimeText;

    @ExcelProperty(value = "结束时间", index = 6)
    @Schema(description = "报警结束时间；未结束时为「报警中」", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endTimeText;

    @ExcelProperty(value = "时长(分钟)", index = 7)
    @Schema(description = "持续时长，单位分钟", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal timeSpan;

    @ExcelProperty(value = "车组", index = 8)
    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @ExcelProperty(value = "车速(km/h)", index = 9)
    @Schema(description = "报警时速度，单位公里/小时", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal velocity;
}
