package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "809 报警督办行：上级平台下发的督办请求与应答状态")
public class UrgeTodoVO {

    @Schema(description = "记录 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "协议内督办 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long superviseId;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "甘A43060")
    private String plateNo;

    @Schema(description = "车牌颜色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "蓝色")
    private String plateColorName;

    @Schema(description = "报警来源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "车载终端")
    private String warnSourceText;

    @Schema(description = "报警类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "超速")
    private String warnTypeText;

    @Schema(description = "报警时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String warnTimeText;

    @Schema(description = "督办截止时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supervisionEndTimeText;

    @Schema(description = "督办级别：紧急 / 一般", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supervisionLevelText;

    @Schema(description = "督办人", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supervisor;

    @Schema(description = "督办人电话", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supervisorTel;

    @Schema(description = "督办人邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
    private String supervisorEmail;

    @Schema(description = "是否已应答", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean acknowledged;

    @Schema(description = "应答结果码；未应答为 null", example = "1")
    private Integer ackResult;

    @Schema(description = "应答结果文案：未应答 / 处理中 / 已处理完毕 / 不做处理 / 将来处理")
    private String ackResultText;

    @Schema(description = "应答时间，格式 yyyy-MM-dd HH:mm:ss；未应答为 null")
    private String ackTimeText;

    @Schema(description = "接收时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createTimeText;
}
