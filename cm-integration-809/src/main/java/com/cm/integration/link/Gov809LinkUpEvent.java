package com.cm.integration.link;

/**
 * 809 链路登录成功事件。补发监听它而不是反向依赖链路管理器，
 * 避免“链路管理器 → 补发服务 → 链路管理器”的循环依赖。
 */
public record Gov809LinkUpEvent(long platformId) {
}
 
