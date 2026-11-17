package com.cm.infrastructure.telemetry;

import com.cm.domain.position.PositionTimePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.ZoneId;

/**
 * 定位链路装配。属性绑定单独成类，MQ 拓扑与消费者按 consumers-enabled 条件装配；
 * 时间质量策略按配置构建一次，网关与消费端共用同一套口径。
 */
@Configuration
@EnableConfigurationProperties({TelemetryProperties.class, TelemetryMqProperties.class})
public class TelemetryConfiguration {

    @Bean
    public PositionTimePolicy positionTimePolicy(TelemetryProperties properties) {
        return new PositionTimePolicy(
                Duration.ofSeconds(properties.getTime().getFutureToleranceSeconds()),
                Duration.ofSeconds(properties.getTime().getStaleThresholdSeconds()),
                Duration.ofSeconds(properties.getTime().getDriftToleranceSeconds()),
                ZoneId.of(properties.getTime().getZone()));
    }
}
