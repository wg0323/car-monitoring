package com.cm.infrastructure.telemetry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定位链路参数。时间阈值、集合名、保存期与租约都从这里取，
 * 生产覆盖走环境变量，不把口径写死在代码里。
 */
@Data
@ConfigurationProperties(prefix = "cm.telemetry")
public class TelemetryProperties {

    private final Time time = new Time();

    private final Dedup dedup = new Dedup();

    private final Latest latest = new Latest();

    private final Publish publish = new Publish();

    private final Broadcast broadcast = new Broadcast();

    private final Mongo mongo = new Mongo();

    private final Online online = new Online();

    private final Mileage mileage = new Mileage();

    /**
     * 设备时间质量阈值。漂移阈值必须小于未来容忍阈值，否则超前方向没有 CLOCK_DRIFT 区间。
     */
    @Data
    public static class Time {

        /** 设备时间超前超过该秒数判 FUTURE。 */
        private int futureToleranceSeconds = 300;

        /** 设备时间落后超过该秒数判 STALE。 */
        private int staleThresholdSeconds = 86400;

        /** 偏差超过该秒数但未触发隔离判 CLOCK_DRIFT。 */
        private int driftToleranceSeconds = 60;

        private String zone = "Asia/Shanghai";
    }

    /**
     * 消费端去重键。TTL 覆盖消费重试与死信重放窗口。
     */
    @Data
    public static class Dedup {

        private String keyPrefix = "cm:telemetry:dedup:";

        private int ttlSeconds = 604800;
    }

    /**
     * Redis 最新位置。版本比较在原子脚本里完成。
     */
    @Data
    public static class Latest {

        private String keyPrefix = "cm:position:";

        private int ttlSeconds = 604800;
    }

    /**
     * 发布确认。一帧多位置时整批共享同一个超时窗口。
     */
    @Data
    public static class Publish {

        private long confirmTimeoutMillis = 3000L;
    }

    /**
     * 实时广播。写最新位置成功后向 Redis 频道发一条精简事件，供管理端 WebSocket 扇出；
     * 关闭后网关行为与 P3 一致，页面只能拿到首屏快照。
     */
    @Data
    public static class Broadcast {

        private boolean enabled = true;

        private String channel = "cm:telemetry:position";
    }

    /**
     * MongoDB 集合。时间序列集合按 deviceTime 分桶、按保存期自动清理。
     */
    @Data
    public static class Mongo {

        private String historyCollection = "position_history";

        private String quarantineCollection = "position_quarantine";

        private int retentionDays = 90;

        private int quarantineRetentionDays = 7;

        private boolean autoInit = true;
    }

    /**
     * 在线租约与断线对账。对账只在网关实例开启。
     */
    @Data
    public static class Online {

        private String keyPrefix = "cm:online:";

        private int leaseTtlSeconds = 180;

        private boolean reconcileEnabled = false;

        private long reconcileIntervalMillis = 60000L;

        private long reconcileInitialDelayMillis = 10000L;
    }

    /**
     * 里程累计。基线、当日记账都在 Redis，落库由定时任务完成；
     * 单点增量超过阈值按脏数据处理，避免设备换机或报文异常把里程抬飞。
     */
    @Data
    public static class Mileage {

        private String baseKeyPrefix = "cm:mileage:base:";

        private String dayKeyPrefix = "cm:mileage:day:";

        private int maxDeltaKilometers = 100;

        private int retentionDays = 7;

        private boolean flushEnabled = false;

        private long flushIntervalMillis = 60000L;

        private long flushInitialDelayMillis = 30000L;
    }
}
