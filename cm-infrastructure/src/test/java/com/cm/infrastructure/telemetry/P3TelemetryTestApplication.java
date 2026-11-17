package com.cm.infrastructure.telemetry;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 定位链路集成测试上下文。只装配属性绑定与时间策略，
 * 具体组件由各测试类按需 @Import，避免把 MQ 监听与定时任务带进来。
 * 定位链路不写 MySQL，但模块依赖里有 MyBatis 与 MySQL 驱动，
 * 不排除数据源自动配置会因为缺少 JDBC URL 而启动失败。
 */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@Import(TelemetryConfiguration.class)
public class P3TelemetryTestApplication {

    /**
     * 基础设施模块不带 actuator，测试上下文自备一个注册表供指标组件使用。
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
