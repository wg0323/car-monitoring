package com.cm.admin.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 历史轨迹点。导出列对齐旧系统 gpsInfo.selectHisotryGpsInfos；
 * 地址与报警中文名依赖逆地理编码与旧字典，本轮不提供。
 */
@ExcelIgnoreUnannotated
@Data
@Schema(description = "历史轨迹点")
public class TrackPointVO {

    @ExcelProperty(value = "车牌号", index = 0)
    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @ExcelProperty(value = "车牌颜色", index = 1)
    @Schema(description = "车牌颜色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "蓝色")
    private String plateColorName;

    @ExcelProperty(value = "车组", index = 2)
    @Schema(description = "所属部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    @ExcelProperty(value = "GPS时间", index = 3)
    @Schema(description = "设备定位时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceTimeText;

    @ExcelProperty(value = "经度", index = 4)
    @Schema(description = "WGS-84 经度，六位小数", requiredMode = Schema.RequiredMode.REQUIRED, example = "121.614682")
    private BigDecimal longitude;

    @ExcelProperty(value = "纬度", index = 5)
    @Schema(description = "WGS-84 纬度，六位小数", requiredMode = Schema.RequiredMode.REQUIRED, example = "38.914003")
    private BigDecimal latitude;

    @ExcelProperty(value = "速度(km/h)", index = 6)
    @Schema(description = "车速，单位公里/小时", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal speedKph;

    @ExcelProperty(value = "记录仪速度(km/h)", index = 7)
    @Schema(description = "记录仪速度，单位公里/小时", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal recorderSpeedKph;

    @ExcelProperty(value = "里程(km)", index = 8)
    @Schema(description = "终端累计里程，单位公里", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal mileage;

    @ExcelProperty(value = "油量(L)", index = 9)
    @Schema(description = "油量，单位升", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal oil;

    @ExcelProperty(value = "方向", index = 10)
    @Schema(description = "方向描述，如 正北 / 东偏北12度", requiredMode = Schema.RequiredMode.REQUIRED)
    private String directionText;

    @ExcelProperty(value = "海拔(米)", index = 11)
    @Schema(description = "海拔，单位米", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer altitude;

    @ExcelProperty(value = "状态", index = 12)
    @Schema(description = "ACC、定位、运营状态描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusText;

    @ExcelProperty(value = "报警标志", index = 13)
    @Schema(description = "终端报警标志，0 表示无报警", requiredMode = Schema.RequiredMode.REQUIRED)
    private String alarmFlagText;

    @ExcelProperty(value = "有效性", index = 14)
    @Schema(description = "定位有效性，取状态位 bit1", requiredMode = Schema.RequiredMode.REQUIRED, example = "有效")
    private String validText;
}
