package com.cm.transport;

import java.util.Objects;

/**
 * 一次终端连接的路由标识。channelActive 不等于已注册，只有拿到有效 SIM 后才建立路由；
 * channelInactive 必须先用 sameConnection 判定路由仍指向本连接，才能删除路由并生成离线事件。
 */
public record ChannelRoute(String simNo, String gatewayId, String channelId, long incarnation) {

    public ChannelRoute {
        Objects.requireNonNull(simNo, "simNo");
        Objects.requireNonNull(gatewayId, "gatewayId");
        Objects.requireNonNull(channelId, "channelId");
    }

    public boolean sameConnection(ChannelRoute currentRoute) {
        if (currentRoute == null) {
            return false;
        }
        return simNo.equals(currentRoute.simNo)
                && incarnation == currentRoute.incarnation
                && channelId.equals(currentRoute.channelId)
                && gatewayId.equals(currentRoute.gatewayId);
    }
}
