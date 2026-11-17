package com.cm.infrastructure.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * P2 持久层配置：Mapper 扫描。
 * 分页用显式 LIMIT 与 COUNT(*)，不引入 3.5.18 拆分出去的 jsqlparser 模块；
 * 雪花 ID 生成器仍由 CmIdConfiguration 提供，本类不重复声明。
 */
@Configuration
@MapperScan("com.cm.infrastructure.persistence.mapper")
public class MybatisPlusConfig {
}
