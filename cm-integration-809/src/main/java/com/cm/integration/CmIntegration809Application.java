package com.cm.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.cm.integration", "com.cm.infrastructure"})
@EnableScheduling
public class CmIntegration809Application {

    public static void main(String[] args) {
        SpringApplication.run(CmIntegration809Application.class, args);
        log.info("cm-integration-809 启动完成：企业侧 JT809 主链路与 12345 从链路监听已就绪");
    }
}
