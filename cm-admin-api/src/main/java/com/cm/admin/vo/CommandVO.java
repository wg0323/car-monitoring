package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "终端命令行：下发对象、状态机终态与时间线")
public class CommandVO {

    @Schema(description = "指令 ID（服务端生成的 UUID），响应与 Outbox 同值",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandId;

    @Schema(description = "目标车辆 ID，序列化为字符串避免前端精度丢失，requiredMode = Schema.RequiredMode.REQUIRED")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long vehicleId;

    @Schema(description = "车牌号", requiredMode = Schema.RequiredMode.REQUIRED, example = "辽B00001")
    private String plateNo;

    @Schema(description = "终端 SIM 号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String simNo;

    @Schema(description = "指令编码，如 0x8201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandCode;

    @Schema(description = "指令名称，如「点名」", requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandName;

    @Schema(description = "指令参数摘要", requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandData;

    @Schema(description = "状态机原值：ACCEPTED / SENT_MQ / SENT_TERMINAL / ACKED / SUCCESS / FAILED / TIMEOUT",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandStatus;

    @Schema(description = "状态中文文案", requiredMode = Schema.RequiredMode.REQUIRED, example = "成功")
    private String statusText;

    @Schema(description = "下发时间，格式 yyyy-MM-dd HH:mm:ss；未下发时为 null",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String sentAtText;

    @Schema(description = "终端应答时间，格式 yyyy-MM-dd HH:mm:ss；未应答时为 null",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String ackAtText;

    @Schema(description = "受理时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createTimeText;

    @Schema(description = "备注（失败原因等）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String remark;
}
