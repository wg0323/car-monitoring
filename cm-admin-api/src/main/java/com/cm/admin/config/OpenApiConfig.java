package com.cm.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_VERSION = "1.0.0";

    @Bean
    public OpenAPI cmOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("车辆监控平台管理端 API")
                .version(API_VERSION)
                .description("P4-1 已接入登录会话、菜单与按钮权限、部门数据范围与车辆列表；其余功能域按 docs/feature-mapping.csv 逐步接入"));
    }
}
