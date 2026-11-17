package com.cm.infrastructure;

import com.cm.infrastructure.command.CmCommandConfiguration;
import com.cm.infrastructure.config.CmIdConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * P2 持久层集成测试用的最小上下文：只加载 DataSource、MyBatis-Plus 与业务 Service，
 * 不引入 cm-admin-api 的 Web / Security / 文档栈。
 * 雪花 ID 必须用生产同一份配置，否则原生 UPSERT 写入的主键会缺失；
 * 指令链路 Service 也在 service 包下，属性绑定要一并装上，否则上下文起不来。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan("com.cm.infrastructure.service")
@MapperScan("com.cm.infrastructure.persistence.mapper")
@Import({CmCommandConfiguration.class, CmIdConfiguration.class})
public class P2TestApplication {
}
