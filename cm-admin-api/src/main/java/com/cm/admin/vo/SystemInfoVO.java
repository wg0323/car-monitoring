package com.cm.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "系统骨架信息")
public class SystemInfoVO {

    @Schema(description = "应用名，对应 spring.application.name", example = "cm-admin-api")
    private String applicationName;

    @Schema(description = "升级阶段标识，当前为 P5", example = "P5")
    private String stage;

    @Schema(description = "当前激活的 Spring Profile，多个以逗号分隔", example = "dev")
    private String activeProfiles;

    @Schema(description = "运行 JDK 版本", example = "17.0.11")
    private String javaVersion;
}
