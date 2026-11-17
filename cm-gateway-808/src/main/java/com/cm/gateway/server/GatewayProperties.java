package com.cm.gateway.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关本地参数。网关标识与超时参数统一放在 cm.command 下，这里只管监听端口与接入鉴权。
 */
@Data
@ConfigurationProperties(prefix = "cm.gateway")
public class GatewayProperties {

    /** JT808 TCP 监听端口，<=0 表示只启动 HTTP 探针不开放监听。 */
    private int tcpPort = 18088;

    /**
     * 平台下发的终端鉴权码（0x8100 带回，终端在 0x0102 回传）。
     * 外置凭据，不入库；生产环境必须由 CM_GATEWAY_AUTH_CODE 注入。
     */
    private String authCode = "";
} 
