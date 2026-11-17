package com.cm.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 消息中心参数。在线集合与广播频道与其它可执行单元共用：
 * 多实例部署时靠 Redis 扇出，单实例行为与本地广播一致。
 */
@Data
@ConfigurationProperties(prefix = "cm.message")
public class MessageProperties {

    /** 聊天与系统广播的 Redis 频道，所有实例订阅同一频道。 */
    private String channel = "cm:message:event";

    /** 在线集合：ZSET，member 为「用户 ID + 姓名 + 会话 ID」，score 为最后活跃毫秒。 */
    private String onlineKey = "cm:message:online";

    /** 握手 Origin 白名单。留空表示只允许同源（Origin 的 host 与请求 Host 一致）。 */
    private List<String> allowedOrigins = List.of();

    /** 单条消息文本上限，超出直接回错误帧，不截断。 */
    private int maxTextLength = 500;

    /** 心跳超时：超过即认为连接已死，先断开再摘在线集合。 */
    private long heartbeatTimeoutMillis = 90000L;

    /** 会话存在性复核间隔，失效即断开连接。 */
    private long checkIntervalMillis = 30000L;

    /** 在线列表返回上限，防止一个部门几千人时把响应撑爆。 */
    private int maxOnlineUsers = 200;
}
