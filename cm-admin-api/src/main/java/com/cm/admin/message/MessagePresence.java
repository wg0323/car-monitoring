package com.cm.admin.message;

import com.cm.admin.config.MessageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线集合。用 Redis ZSET 而不是进程内 Map：管理端多实例部署时，
 * 一个实例发起的私聊要能找到挂在另一个实例上的接收人，在线列表也要跨实例一致。
 *
 * <p>member 由「用户 ID + 姓名 + HTTP 会话 ID」组成，score 是最后活跃毫秒。
 * 同一账号开多个标签页就有多个 member，查询时按用户去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePresence {

    /** 字段分隔符用不可打印字符，避免姓名里出现分隔符把 member 拆错。 */
    private static final String SEP = String.valueOf((char) 0x01);

    private final StringRedisTemplate redisTemplate;

    private final MessageProperties properties;

    public void join(Long userId, String displayName, String httpSessionId, long nowMillis) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.opsForZSet().add(properties.getOnlineKey(),
                    member(userId, displayName, httpSessionId), nowMillis);
        } catch (RuntimeException ex) {
            // 在线集合只影响「找不找得到人」，Redis 抖动时不该把已建立的连接一起踢掉。
            log.warn("在线集合写入失败, userId={}", userId, ex);
        }
    }

    public void leave(Long userId, String displayName, String httpSessionId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.opsForZSet().remove(properties.getOnlineKey(),
                    member(userId, displayName, httpSessionId));
        } catch (RuntimeException ex) {
            log.warn("在线集合清理失败, userId={}", userId, ex);
        }
    }

    /**
     * 在线用户列表。先摘掉超时 member，再按用户去重；同一用户多标签页只出现一次。
     */
    public List<OnlineUser> onlineUsers() {
        long now = System.currentTimeMillis();
        long expireBefore = now - properties.getHeartbeatTimeoutMillis();
        try {
            ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
            ops.removeRangeByScore(properties.getOnlineKey(), 0, expireBefore);
            Set<String> members = ops.rangeByScore(properties.getOnlineKey(), expireBefore,
                    Double.POSITIVE_INFINITY);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            Map<String, OnlineUser> byUserId = new LinkedHashMap<>();
            for (String member : members) {
                OnlineUser user = parse(member);
                if (user == null) {
                    continue;
                }
                byUserId.putIfAbsent(user.userId(), user);
                if (byUserId.size() >= properties.getMaxOnlineUsers()) {
                    break;
                }
            }
            return new ArrayList<>(byUserId.values());
        } catch (RuntimeException ex) {
            log.warn("在线集合查询失败", ex);
            return List.of();
        }
    }

    /** 目标是否在线。成员前缀按用户 ID 精确匹配，避免 1 命中 12。 */
    public boolean online(Long userId) {
        if (userId == null) {
            return false;
        }
        long expireBefore = System.currentTimeMillis() - properties.getHeartbeatTimeoutMillis();
        try {
            Set<String> members = redisTemplate.opsForZSet().rangeByScore(properties.getOnlineKey(),
                    expireBefore, Double.POSITIVE_INFINITY);
            if (members == null) {
                return false;
            }
            String prefix = userId + SEP;
            return members.stream().anyMatch(member -> member.startsWith(prefix));
        } catch (RuntimeException ex) {
            log.warn("在线判定失败, userId={}", userId, ex);
            return false;
        }
    }

    private String member(Long userId, String displayName, String httpSessionId) {
        return userId + SEP + (displayName == null ? "" : displayName) + SEP + httpSessionId;
    }

    private OnlineUser parse(String member) {
        String[] parts = member.split(SEP, 3);
        if (parts.length < 2 || parts[0].isBlank()) {
            return null;
        }
        return new OnlineUser(parts[0], parts[1]);
    }

    /** 在线用户：ID 留字符串，前端与帧里的用户 ID 同一口径。 */
    public record OnlineUser(String userId, String displayName) {
    }
}
