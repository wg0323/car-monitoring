package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Schema(description = "809 指令与报文行：本端下发与上级下发共用同一张表")
public class GovCommandVO {

    @Schema(description = "记录 ID，序列化为字符串避免前端精度丢失", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "平台接入码", requiredMode = Schema.RequiredMode.REQUIRED, example = "8992")
    private Long platformId;

    @Schema(description = "帧流水号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sn;

    @Schema(description = "消息 ID（十进制）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer cmd;

    @Schema(description = "消息 ID 文案，如 0x1001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cmdText;

    @Schema(description = "子类型标识，链路命令为 0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer subCmd;

    @Schema(description = "类型描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "[0x1001]主链路登录请求")
    private String descr;

    @Schema(description = "车牌号，无车辆关联时为空串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String plateNo;

    @Schema(description = "车牌颜色编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer plateColor;

    @Schema(description = "指令内容摘要", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cmdData;

    @Schema(description = "记录来源：SEND-本端下发，RECEIVE-上级下发", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "来源文案", requiredMode = Schema.RequiredMode.REQUIRED, example = "本端下发")
    private String sourceText;

    @Schema(description = "状态：待下发 / 执行中 / 发送成功 / 发送失败 / 接收成功 / 重复接收 / 失败",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String commandStatus;

    @Schema(description = "创建时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String createTimeText;

    @Schema(description = "执行时间，格式 yyyy-MM-dd HH:mm:ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String updateTimeText;
}
