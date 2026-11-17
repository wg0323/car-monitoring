package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CmCommandMqProperties;
import com.cm.infrastructure.command.CommandJson;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.entity.CmMqDeadLetter;
import com.cm.infrastructure.persistence.mapper.CmMqDeadLetterMapper;
import com.cm.infrastructure.service.CmCommandPublisher;
import com.cm.infrastructure.service.CmMqDeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 死信持久化与重放。
 * 落库用 messageId 做唯一键，DLX 重投不会产生重复记录；重放先条件改状态再发布，
 * 并发重放只有一次能成功，业务侧再由状态机与尝试唯一键保证只推进一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmMqDeadLetterServiceImpl implements CmMqDeadLetterService {

    private static final String STATUS_PENDING = "PENDING";

    private static final int MESSAGE_ID_MAX_LENGTH = 96;

    private static final int EXCHANGE_MAX_LENGTH = 64;

    private static final int ROUTING_KEY_MAX_LENGTH = 96;

    private static final int COMMAND_ID_MAX_LENGTH = 64;

    private static final int PAYLOAD_MAX_LENGTH = 4000;

    private static final int HEADERS_MAX_LENGTH = 1000;

    private static final int REASON_MAX_LENGTH = 255;

    private final CmMqDeadLetterMapper deadLetterMapper;

    private final CmCommandMqProperties mqProperties;

    /**
     * 重放时才取发布器：发布模板的回调依赖状态服务，状态服务又依赖本服务，
     * 构造期直接注入会形成循环，这里延迟到实际重放时获取。
     */
    private final ObjectProvider<CmCommandPublisher> publisherProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void parkOutboxFailure(CmCommandOutbox row, int publishAttempt, String reason) {
        CmMqDeadLetter letter = new CmMqDeadLetter();
        letter.setMessageId(truncate("OUTBOX-" + row.getCommandId() + "-" + publishAttempt, MESSAGE_ID_MAX_LENGTH));
        letter.setSourceQueue("");
        letter.setExchangeName(truncate(row.getExchangeName(), EXCHANGE_MAX_LENGTH));
        letter.setRoutingKey(truncate(row.getRoutingKey(), ROUTING_KEY_MAX_LENGTH));
        letter.setCommandId(truncate(row.getCommandId(), COMMAND_ID_MAX_LENGTH));
        letter.setPayload(truncate(row.getPayload(), PAYLOAD_MAX_LENGTH));
        letter.setHeadersJson("");
        letter.setReason(truncate(reason, REASON_MAX_LENGTH));
        letter.setDeadStatus(STATUS_PENDING);
        letter.setReplayCount(0);
        insertIfAbsent(letter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void parkDeadMessage(Message message, String sourceQueue, String reason) {
        MessageProperties properties = message.getMessageProperties();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String messageId = properties.getMessageId();
        if (messageId == null || messageId.isBlank()) {
            messageId = digest(payload);
        }
        String originalExchange = originalExchange(properties);
        String originalRoutingKey = originalRoutingKey(properties);
        CmMqDeadLetter letter = new CmMqDeadLetter();
        letter.setMessageId(truncate(messageId, MESSAGE_ID_MAX_LENGTH));
        letter.setSourceQueue(truncate(sourceQueue, ROUTING_KEY_MAX_LENGTH));
        letter.setExchangeName(truncate(originalExchange, EXCHANGE_MAX_LENGTH));
        letter.setRoutingKey(truncate(originalRoutingKey, ROUTING_KEY_MAX_LENGTH));
        letter.setCommandId(resolveCommandId(sourceQueue, properties.getMessageId()));
        letter.setPayload(truncate(payload, PAYLOAD_MAX_LENGTH));
        letter.setHeadersJson(truncate(CommandJson.write(properties.getHeaders()), HEADERS_MAX_LENGTH));
        letter.setReason(truncate(reason, REASON_MAX_LENGTH));
        letter.setDeadStatus(STATUS_PENDING);
        letter.setReplayCount(0);
        insertIfAbsent(letter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replay(Long id) {
        CmMqDeadLetter letter = deadLetterMapper.selectById(id);
        if (letter == null || !STATUS_PENDING.equals(letter.getDeadStatus())) {
            return false;
        }
        if (letter.getExchangeName() == null || letter.getExchangeName().isBlank()
                || letter.getRoutingKey() == null || letter.getRoutingKey().isBlank()) {
            log.warn("死信缺少原始路由，拒绝重放, id={}, sourceQueue={}", id, letter.getSourceQueue());
            return false;
        }
        if (deadLetterMapper.markReplayed(id, LocalDateTime.now()) != 1) {
            return false;
        }
        publisherProvider.getObject().replay(letter.getExchangeName(), letter.getRoutingKey(),
                letter.getMessageId(), letter.getPayload());
        log.info("死信已重放, id={}, commandId={}, routingKey={}", id, letter.getCommandId(), letter.getRoutingKey());
        return true;
    }

    /**
     * 死信被投递到 DLQ 时，消息属性里的 exchange/routingKey 已经是 DLX 与 DLQ 的值，
     * 原始业务路由只能从 x-death 里取；取不到就留空，重放时拒绝，避免把消息又投回死信队列。
     */
    private String originalExchange(MessageProperties properties) {
        Object first = firstDeath(properties);
        if (first instanceof Map<?, ?> death && death.get("exchange") instanceof String exchange) {
            return exchange;
        }
        return "";
    }

    private String originalRoutingKey(MessageProperties properties) {
        Object first = firstDeath(properties);
        if (first instanceof Map<?, ?> death && death.get("routing-keys") instanceof List<?> keys
                && !keys.isEmpty() && keys.get(0) instanceof String routingKey) {
            return routingKey;
        }
        return "";
    }

    private Object firstDeath(MessageProperties properties) {
        Object xDeath = properties.getHeaders().get("x-death");
        if (xDeath instanceof List<?> deaths && !deaths.isEmpty()) {
            return deaths.get(0);
        }
        return null;
    }

    /**
     * 只有下行死信的消息 ID 才是 commandId；应答死信的 messageId 是事件 ID，不能污染业务键。
     * sourceQueue 是消费到的队列名（即 DLQ），因此业务队列与其死信队列都要认。
     */
    private String resolveCommandId(String sourceQueue, String messageId) {
        if (messageId == null || sourceQueue == null) {
            return "";
        }
        boolean fromDownlink = mqProperties.getDownlinkQueue().equals(sourceQueue)
                || mqProperties.getDownlinkDeadQueue().equals(sourceQueue);
        return fromDownlink ? truncate(messageId, COMMAND_ID_MAX_LENGTH) : "";
    }

    private void insertIfAbsent(CmMqDeadLetter letter) {
        try {
            deadLetterMapper.insert(letter);
        } catch (DuplicateKeyException ex) {
            log.info("死信已存在，跳过重复落库, messageId={}", letter.getMessageId());
        }
    }

    private String digest(String payload) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return "SHA256-" + hex;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
