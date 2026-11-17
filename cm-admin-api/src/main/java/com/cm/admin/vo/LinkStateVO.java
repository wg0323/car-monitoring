package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "809 企业侧主从链路状态快照")
public class LinkStateVO {

    @Schema(description = "本端平台接入码", requiredMode = Schema.RequiredMode.REQUIRED, example = "8992")
    private Long platformId;

    @Schema(description = "主链路状态：DISCONNECTED-CONNECTED-LOGGED_IN",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "LOGGED_IN")
    private String mainLinkState;

    @Schema(description = "从链路状态：DISCONNECTED-CONNECTED-LOGGED_IN",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "LOGGED_IN")
    private String subLinkState;

    @Schema(description = "待补发报文条数", requiredMode = Schema.RequiredMode.REQUIRED)
    private long pendingCount;

    @Schema(description = "最后一次链路错误", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastError;
}
