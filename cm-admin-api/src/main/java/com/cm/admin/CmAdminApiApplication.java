package com.cm.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.cm.admin", "com.cm.infrastructure"})
public class CmAdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmAdminApiApplication.class, args);
        log.info("cm-admin-api 启动完成；P2 已接入 MySQL 持久层，业务接口在 P4 接入");
    }
}
