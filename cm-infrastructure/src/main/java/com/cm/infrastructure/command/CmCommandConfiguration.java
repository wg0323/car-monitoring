package com.cm.infrastructure.command;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 指令链路的公共装配：属性绑定与定时任务开关。
 * 定时任务只有 relay 与超时清扫两类，条件更新保证多实例重复执行不会破坏状态。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({CmCommandProperties.class, CmCommandMqProperties.class})
public class CmCommandConfiguration {
}
