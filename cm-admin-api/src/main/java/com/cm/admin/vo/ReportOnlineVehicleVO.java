package com.cm.admin.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@ExcelIgnoreUnannotated
@Data
@Schema(description = "当前车辆在线情况报表行")
public class ReportOnlineVehicleVO {

    @ExcelProperty(value = "车牌号", index = 0)
    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @ExcelProperty(value = "车牌颜色", index = 1)
    @Schema(description = "车牌颜色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "蓝色")
    private String plateColorName;

    @ExcelProperty(value = "终端卡号", index = 2)
    @Schema(description = "终端 SIM 号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @ExcelProperty(value = "车组", index = 3)
    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @ExcelProperty(value = "上线状态", index = 4)
    @Schema(description = "在线状态：在线 / 离线", requiredMode = Schema.RequiredMode.REQUIRED)
    private String onlineStatusName;

    @ExcelProperty(value = "上线时间", index = 5)
    @Schema(description = "最近一次定位时间，格式 yyyy-MM-dd HH:mm:ss；无位置时为空", requiredMode = Schema.RequiredMode.REQUIRED)
    private String onlineTimeText;

    @ExcelProperty(value = "离线时长(分钟)", index = 6)
    @Schema(description = "离线时长，单位分钟；在线或无位置时为空", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long offlineMinutes;

    @ExcelProperty(value = "运行状态", index = 7)
    @Schema(description = "车辆运行状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String runStatus;

    @ExcelProperty(value = "车辆类型", index = 8)
    @Schema(description = "车辆类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String vehicleType;

    @ExcelProperty(value = "入网时间", index = 9)
    @Schema(description = "入网时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String installDateText;
}
