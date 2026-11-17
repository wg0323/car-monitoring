package com.cm.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.cm.gateway", "com.cm.infrastructure"})
public class CmGateway808Application {

    public static void main(String[] args) {
        SpringApplication.run(CmGateway808Application.class, args);
        log.info("cm-gateway-808 启动完成；JT808 监听、指令下行与终端应答转发已接入");
    }
}
