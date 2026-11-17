package com.cm.infrastructure;

import com.cm.infrastructure.command.CmCommandConfiguration;
import com.cm.infrastructure.config.CmIdConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * P3 指令链路的集成测试上下文：只装配业务 Service、Mapper 与指令链路属性，
 * 不引入 Web、安全、MQ 监听与定时清扫，避免测试依赖 RabbitMQ 与调度线程。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("com.cm.infrastructure.service")
@MapperScan("com.cm.infrastructure.persistence.mapper")
@Import({CmCommandConfiguration.class, CmIdConfiguration.class})
public class P3TestApplication {
}
