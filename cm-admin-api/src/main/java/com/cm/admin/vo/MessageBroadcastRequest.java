package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统广播请求体。广播不落库，发出去只存在于当期在线连接的消息列表里。
 */
@Data
@Schema(description = "系统广播请求：全平台在线账号立即收到")
public class MessageBroadcastRequest {

    @Schema(description = "广播正文，1~500 字", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "今晚 22:00 起进行平台升级，请提前退出系统")
    @NotBlank(message = "广播内容不能为空")
    @Size(max = 500, message = "广播内容不能超过 500 字")
    private String text;
}
