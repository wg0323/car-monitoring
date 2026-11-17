package com.cm.gov;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.cm.gov", "com.cm.infrastructure"})
public class CmGov809Application {

    public static void main(String[] args) {
        SpringApplication.run(CmGov809Application.class, args);
        log.info("cm-gov-809 启动完成：19100 监管侧监听与下级平台主从链路已就绪");
    }
}
