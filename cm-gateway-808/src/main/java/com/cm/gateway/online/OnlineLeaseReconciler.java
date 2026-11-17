package com.cm.gateway.online;

import com.cm.gateway.downlink.TerminalChannelRegistry;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.telemetry.OnlineLease;
import com.cm.infrastructure.telemetry.OnlineLeaseStore;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 在线租约对账。网关崩溃前写入的租约没有进程能删，重启后本地连接表为空，
 * 这里按实例名扫描 Redis 残留并清理；本地仍有连接的 SIM 一律不动。
 * 只处理本实例名下的租约，其他网关的残留由它们自己重启后清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cm.telemetry.online", name = "reconcile-enabled", havingValue = "true")
public class OnlineLeaseReconciler {

    private final OnlineLeaseStore onlineLeaseStore;

    private final TerminalChannelRegistry channelRegistry;

    private final CmCommandProperties commandProperties;

    private final TelemetryMetrics telemetryMetrics;

    @Scheduled(initialDelayString = "${cm.telemetry.online.reconcile-initial-delay-millis:10000}",
            fixedDelayString = "${cm.telemetry.online.reconcile-interval-millis:60000}")
    public void reconcile() {
        String gatewayId = commandProperties.getGatewayId();
        List<String> simNos = onlineLeaseStore.scanByGateway(gatewayId);
        int removed = 0;
        for (String simNo : simNos) {
            if (channelRegistry.find(simNo).isPresent()) {
                continue;
            }
            Optional<OnlineLease> lease = onlineLeaseStore.find(simNo);
            if (lease.isEmpty()) {
                continue;
            }
            if (onlineLeaseStore.removeIfMatch(simNo, lease.get().gatewayId(), lease.get().incarnation())) {
                removed++;
                telemetryMetrics.offline();
                log.info("对账清理残留在线租约, simNo={}, incarnation={}", simNo, lease.get().incarnation());
            }
        }
        if (removed > 0) {
            telemetryMetrics.leaseReconciled();
            log.info("在线租约对账完成, gatewayId={}, removed={}", gatewayId, removed);
        }
    }
}
