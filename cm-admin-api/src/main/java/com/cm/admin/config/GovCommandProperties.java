package com.cm.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理端使用的 809 接入参数。只放本端接入码：链路命令落库时需要，
 * 与 cm-integration-809 共用同一环境变量，保证两边写入与查询用同一平台标识。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cm.gov809")
public class GovCommandProperties {

    /** 本端平台接入码，默认取旧仓库 gps-jt809server/config.properties 的实测值。 */
    private long platformId = 8992L;
}
