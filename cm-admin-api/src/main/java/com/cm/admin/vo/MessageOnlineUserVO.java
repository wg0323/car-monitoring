package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线用户行。ID 与 WebSocket 帧里的用户 ID 同一口径，都是字符串。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息中心在线用户")
public class MessageOnlineUserVO {

    @Schema(description = "用户 ID，字符串形式", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @Schema(description = "用户显示名", requiredMode = Schema.RequiredMode.REQUIRED, example = "监控员一")
    private String displayName;
}
