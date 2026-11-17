package com.cm.admin.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@ExcelIgnoreUnannotated
@Data
@Schema(description = "车辆上下线记录报表行")
public class ReportOnlineRecordVO {

    @ExcelProperty(value = "车牌号", index = 0)
    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @ExcelProperty(value = "车牌颜色", index = 1)
    @Schema(description = "车牌颜色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "蓝色")
    private String plateColorName;

    @ExcelProperty(value = "记录类型", index = 2)
    @Schema(description = "记录类型：上线记录 / 下线记录", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recordTypeName;

    @ExcelProperty(value = "地点", index = 3)
    @Schema(description = "事件地点描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String location;

    @ExcelProperty(value = "开始时间", index = 4)
    @Schema(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startTimeText;

    @ExcelProperty(value = "结束时间", index = 5)
    @Schema(description = "结束时间；未结束时为「进行中」", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endTimeText;

    @ExcelProperty(value = "时长(分钟)", index = 6)
    @Schema(description = "持续时长，单位分钟", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal timeSpan;

    @ExcelProperty(value = "车组", index = 7)
    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;
}
