package com.cm.gateway.server;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关侧业务线程池。
 * EventLoop 只做网络与编解码，应答转发与定位发布这类阻塞操作必须切到这里执行；
 * 队列有界且拒绝策略明确，堆满时丢弃并告警，不在 EventLoop 上退化为同步执行。
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayExecutorConfiguration {

    private static final int CORE_POOL_SIZE = 2;

    private static final int MAX_POOL_SIZE = 4;

    private static final long KEEP_ALIVE_SECONDS = 60L;

    private static final int QUEUE_CAPACITY = 2048;

    /** 定位发布线程数按模拟基线 1667 包/s、单次 confirm 约 2ms 估算，八个线程留出约两倍余量。 */
    private static final int TELEMETRY_CORE_POOL_SIZE = 8;

    private static final int TELEMETRY_MAX_POOL_SIZE = 8;

    private static final int TELEMETRY_QUEUE_CAPACITY = 4096;

    /**
     * 注册判定线程：每次注册要查库，不能与心跳续租、断线清理共用 ackPublishExecutor，
     * 否则库慢时会把在线状态维护一起堵死，放大成整批车「离线」。
     */
    private static final int REGISTRATION_CORE_POOL_SIZE = 2;

    private static final int REGISTRATION_MAX_POOL_SIZE = 4;

    private static final int REGISTRATION_QUEUE_CAPACITY = 512;

    @Bean(name = "ackPublishExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor ackPublishExecutor() {
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), threadFactory("cm-jt808-ack-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 定位发布专用线程：一批位置消息的发布与 confirm 等待都在这里完成，
     * 与终端应答转发隔离，避免互相挤占队列。
     */
    @Bean(name = "telemetryPublishExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor telemetryPublishExecutor() {
        return new ThreadPoolExecutor(TELEMETRY_CORE_POOL_SIZE, TELEMETRY_MAX_POOL_SIZE, KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(TELEMETRY_QUEUE_CAPACITY),
                threadFactory("cm-jt808-position-"), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "registrationExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor registrationExecutor() {
        return new ThreadPoolExecutor(REGISTRATION_CORE_POOL_SIZE, REGISTRATION_MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new ArrayBlockingQueue<>(REGISTRATION_QUEUE_CAPACITY),
                threadFactory("cm-jt808-register-"), new ThreadPoolExecutor.AbortPolicy());
    }

    private ThreadFactory threadFactory(String prefix) {
        AtomicInteger index = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
