package com.cm.admin.service;

import com.cm.admin.vo.MessageOnlineUserVO;

import java.util.List;

/**
 * 消息中心的应用层：在线列表与系统广播。聊天本身走 WebSocket 帧，
 * 不经过 REST；广播走 REST 是因为权限与审计在 HTTP 层更容易统一处理。
 */
public interface MessageAppService {

    /**
     * 在线用户：来自 Redis 在线集合，按用户去重，超过上限截断。
     */
    List<MessageOnlineUserVO> onlineUsers();

    /**
     * 发布系统广播：写入 Redis 频道，由各实例投递给本机全部在线连接。
     */
    void broadcast(Long userId, String text);
}
