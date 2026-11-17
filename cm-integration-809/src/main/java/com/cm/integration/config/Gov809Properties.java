package com.cm.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业侧 809 链路参数。默认值取自旧仓库 gps-jt809server/config.properties 的实测口径：
 * 接入码 8992、账号 8992、密码 12345678、上级 127.0.0.1:19100、从链路 12345。
 * 账号密码只用于 dev 本地联调，生产走应用外置配置注入。
 */
@Data
@ConfigurationProperties(prefix = "cm.gov809")
public class Gov809Properties {

    /** 关闭后不监听也不连出，供只跑 HTTP 探针的调试场景使用。 */
    private boolean enabled = true;

    private long platformId = 8992L;

    private long loginUserId = 8992L;

    private String loginPassword = "12345678";

    private String upstreamHost = "127.0.0.1";

    private int upstreamPort = 19100;

    private String subLinkBindAddress = "0.0.0.0";

    /** 登录请求里上报告给上级的从链路地址，本地联调指向 127.0.0.1。 */
    private String subLinkAdvertiseIp = "127.0.0.1";

    private int subLinkPort = 12345;

    /** 主链路心跳间隔，旧实现为 20 秒。 */
    private int heartbeatIntervalSeconds = 20;

    private int idleTimeoutSeconds = 60;

    /** 指数退避与抖动，避免上级重启时被集中重连。 */
    private long reconnectInitialMillis = 5_000L;

    private long reconnectMaxMillis = 60_000L;

    private long writeTimeoutMillis = 10_000L;

    /** 单帧补发条数：0x1203 后续长度只有 4 字节，200 条留给容量上限。 */
    private int replayBatchSize = 30;

    private int replayMaxRows = 5_000;

    private long checkpointFlushMillis = 1_000L;

    /** 定位时间转本地时间的时区，与定位链路保持一致。 */
    private String zone = "Asia/Shanghai";
}
 
