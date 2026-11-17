package com.cm.infrastructure.gov809;

import com.cm.infrastructure.config.CmIdConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * 809 持久化集成测试上下文：只装 MQ 拓扑属性、809 服务与 Mapper，
 * 不引入 Web、MQ 监听与 Netty，避免测试依赖 broker 与端口。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("com.cm.infrastructure.gov809")
@MapperScan("com.cm.infrastructure.persistence.mapper")
@Import(CmIdConfiguration.class)
public class Gov809TestApplication {
}
 
