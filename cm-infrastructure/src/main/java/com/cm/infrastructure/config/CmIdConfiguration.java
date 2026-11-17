package com.cm.infrastructure.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 生成器。多实例部署必须由 CM_ID_WORKER_ID / CM_ID_DATACENTER_ID 注入唯一组合，
 * 不能让所有实例共用默认值，也不能依赖默认推导。
 */
@Configuration
public class CmIdConfiguration {

    private static final int MAX_NODE_ID = 31;

    @Bean
    public IdentifierGenerator identifierGenerator(
            @Value("${cm.id.worker-id:0}") int workerId,
            @Value("${cm.id.datacenter-id:0}") int datacenterId) {
        if (workerId < 0 || workerId > MAX_NODE_ID || datacenterId < 0 || datacenterId > MAX_NODE_ID) {
            throw new IllegalArgumentException("workerId and datacenterId must be in [0, 31]");
        }
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }
}
