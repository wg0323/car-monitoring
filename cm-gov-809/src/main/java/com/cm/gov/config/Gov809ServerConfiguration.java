package com.cm.gov.config;

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
 * 监管侧传输层装配。监听与回连共用同一组 EventLoop，业务落库放独立线程池，
 * 登录校验与状态落库不得在 EventLoop 里做。
 */
@Configuration
@EnableConfigurationProperties(GovSuperiorProperties.class)
public class Gov809ServerConfiguration {

    private static final int BUSINESS_CORE_THREADS = 2;

    private static final int BUSINESS_MAX_THREADS = 4;

    private static final long BUSINESS_KEEP_ALIVE_SECONDS = 60L;

    private static final int BUSINESS_QUEUE_CAPACITY = 2_000;

    @Bean(destroyMethod = "shutdownGracefully")
    public EventLoopGroup gov809EventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(0, new DefaultThreadFactory("cm-jt809-gov"),
                NioIoHandler.newFactory());
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor gov809BusinessExecutor() {
        return new ThreadPoolExecutor(BUSINESS_CORE_THREADS, BUSINESS_MAX_THREADS,
                BUSINESS_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(BUSINESS_QUEUE_CAPACITY),
                new DefaultThreadFactory("cm-jt809-gov-biz"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 显式提供调度器：EventLoopGroup 也实现了 ScheduledExecutorService，
     * 不指定的话 Spring 会拿它当 TaskScheduler，定时任务会因时基不同算出负的初始延迟。
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("cm-jt809-gov-sched-");
        scheduler.initialize();
        return scheduler;
    }
}
 
