package com.cm.integration.config;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 809 传输层的线程装配。客户端重连与从链路监听共用同一个 EventLoopGroup：
 * 旧实现每次重连都新建线程池，断链风暴时会持续泄漏线程。
 */
@Configuration
@EnableConfigurationProperties(Gov809Properties.class)
public class Gov809NettyConfiguration {

    private static final String EVENT_LOOP_NAME = "cm-jt809";

    private static final int BUSINESS_CORE_THREADS = 2;

    private static final int BUSINESS_MAX_THREADS = 4;

    private static final long BUSINESS_KEEP_ALIVE_SECONDS = 60L;

    private static final int BUSINESS_QUEUE_CAPACITY = 2_000;

    @Bean(destroyMethod = "shutdownGracefully")
    public EventLoopGroup jt809EventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(0, new DefaultThreadFactory(EVENT_LOOP_NAME),
                NioIoHandler.newFactory());
    }

    /**
     * 业务线程池：链路状态、督办落库、指令日志都不允许在 EventLoop 里做，
     * 否则一次数据库卡顿会把整条链路的读写拖住。
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor gov809BusinessExecutor() {
        return new ThreadPoolExecutor(BUSINESS_CORE_THREADS, BUSINESS_MAX_THREADS,
                BUSINESS_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(BUSINESS_QUEUE_CAPACITY),
                new DefaultThreadFactory("cm-jt809-biz"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 显式提供调度器。EventLoopGroup 也实现了 ScheduledExecutorService，
     * 不指定的话 Spring 会拿它当 TaskScheduler，定时任务会因时基不同算出负的初始延迟。
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("cm-jt809-sched-");
        scheduler.initialize();
        return scheduler;
    }
}
 
