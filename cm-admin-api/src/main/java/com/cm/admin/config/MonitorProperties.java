package com.cm.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 实时监控参数。地图 key 走环境变量注入，不写进仓库与前端构建产物；
 * 订阅与心跳上限集中在这里，避免散落在 Handler 里当魔法值。
 */
@Data
@ConfigurationProperties(prefix = "cm.monitor")
public class MonitorProperties {

    /** 与网关 shared 的广播频道，两端必须一致。 */
    private String positionChannel = "cm:telemetry:position";

    /** 状态栏聚合结果缓存秒数：SCAN 与管道读取都不便宜，页面 30s 才刷一次。小于等于 0 表示关闭缓存。 */
    private long summaryCacheSeconds = 10;

    /** 握手 Origin 白名单。留空表示只允许同源（Origin 的 host 与请求 Host 一致）。 */
    private List<String> allowedOrigins = List.of();

    private final MapConfig map = new MapConfig();

    private final Subscribe subscribe = new Subscribe();

    private final Session session = new Session();

    @Data
    public static class MapConfig {

        private String engine = "amap";

        private String key = "";

        private String securityCode = "";

        /** cm_system_config 缺行时的兜底中心，取旧库默认值。 */
        private double fallbackLat = 34;

        private double fallbackLng = 121;

        private int fallbackZoom = 10;
    }

    @Data
    public static class Subscribe {

        /** 单连接订阅上限。实时页只订阅选中车辆，超过这个量说明用错了页面。 */
        private int maxVehiclesPerSession = 200;
    }

    @Data
    public static class Session {

        /** 会话存在性检查间隔，失效即断开连接。 */
        private long checkIntervalMillis = 30000L;

        /** 账号状态复核间隔。比会话检查稀疏：禁用一个账号不需要秒级生效。 */
        private long accountCheckIntervalMillis = 300000L;

        /** 客户端心跳超时，超过即认为连接已死。 */
        private long heartbeatTimeoutMillis = 90000L;
    }
}
