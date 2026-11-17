package com.cm.admin.service.impl;

import com.cm.admin.message.MessageDelivery;
import com.cm.admin.message.MessageSessionRegistry;
import com.cm.admin.service.MessageAppService;
import com.cm.admin.vo.MessageOnlineUserVO;
import com.cm.admin.config.MessageProperties;
import com.cm.domain.exception.CmBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息中心应用层实现。系统广播不落库：旧系统的广播也只是往 WebSocket 会话里推一条文本，
 * 没有历史表；要补历史得先有消息表与已读模型，不在本切片范围。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageAppServiceImpl implements MessageAppService {

    private final MessageSessionRegistry registry;

    private final MessageDelivery delivery;

    private final MessageProperties properties;

    @Override
    public List<MessageOnlineUserVO> onlineUsers() {
        return registry.onlineUsers().stream()
                .map(user -> new MessageOnlineUserVO(user.userId(), user.displayName()))
                .toList();
    }

    @Override
    public void broadcast(Long userId, String text) {
        if (text == null || text.isBlank()) {
            throw new CmBusinessException("广播内容不能为空");
        }
        String content = text.trim();
        if (content.length() > properties.getMaxTextLength()) {
            throw new CmBusinessException("广播内容不能超过 " + properties.getMaxTextLength() + " 字");
        }
        delivery.publishSystem(content);
        log.info("系统广播已发布, userId={}, 在线用户数={}", userId, registry.onlineUsers().size());
    }
}
