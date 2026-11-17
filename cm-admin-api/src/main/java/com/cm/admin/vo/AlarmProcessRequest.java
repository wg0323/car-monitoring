package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 报警处理请求体。处理结果本轮只有「已处理」一种，由服务端固定写入，
 * 不接受客户端自定义结果码，避免出现页面上写「解除」但终端根本没收到指令的假状态。
 */
@Data
@Schema(description = "报警处理请求：单条与批量共用，只有备注可填")
public class AlarmProcessRequest {

    @Schema(description = "处理备注，可为空；超过 255 字符拒绝", example = "已电话核实，驾驶员确认超速")
    @Size(max = 255, message = "处理备注不能超过 255 字符")
    private String remark;
}
