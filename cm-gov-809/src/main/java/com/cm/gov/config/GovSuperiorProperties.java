package com.cm.gov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监管侧 809 参数。19100 是本平台作为上级监听下级平台登录的端口（旧系统实测口径），
 * 下级登录成功后由本端向它声明的从链路地址回连并发 0x9001。
 */
@Data
@ConfigurationProperties(prefix = "cm.gov809.server")
public class GovSuperiorProperties {

    private boolean enabled = true;

    private int listenPort = 19100;

    private String listenAddress = "0.0.0.0";

    /** 从链路心跳 0x9005 间隔，旧实现也是 20 秒量级。 */
    private int heartbeatIntervalSeconds = 20;

    private int idleTimeoutSeconds = 60;

    private long writeTimeoutMillis = 10_000L;

    private int subLinkConnectTimeoutMillis = 5_000;

    /** 断连判定：从链路连续无心跳超过该秒数即视为断开。 */
    private int subLinkIdleTimeoutSeconds = 25;

    private String zone = "Asia/Shanghai";
}
 
