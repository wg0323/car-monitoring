package com.cm.infrastructure.service.impl;

import com.cm.infrastructure.command.CmCommandMqProperties;
import com.cm.infrastructure.persistence.entity.CmMqDeadLetter;
import com.cm.infrastructure.persistence.mapper.CmMqDeadLetterMapper;
import com.cm.infrastructure.service.CmCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 死信落库与重放的关键约束：
 * 原始业务路由只能来自 x-death，取不到就拒绝重放，避免把消息又投回死信队列。
 */
@ExtendWith(MockitoExtension.class)
class CmMqDeadLetterServiceTest {

    private static final String DOWNLINK_QUEUE = "command.downlink.gw-1";

    private static final String DOWNLINK_DLQ = "command.downlink.dlq";

    private static final String ACK_DLQ = "command.ack.dlq";

    @Mock
    private CmMqDeadLetterMapper deadLetterMapper;

    @Mock
    private ObjectProvider<CmCommandPublisher> publisherProvider;

    private CmMqDeadLetterServiceImpl deadLetterService;

    @BeforeEach
    void setUp() {
        deadLetterService = new CmMqDeadLetterServiceImpl(deadLetterMapper,
                new CmCommandMqProperties(), publisherProvider);
    }

    @Test
    @DisplayName("下行死信从 x-death 取回原始 exchange 与 routingKey，并保留 commandId")
    void should_take_original_route_from_x_death() {
        Message message = message("cmd-0001", List.of(Map.of(
                "exchange", "cm.command",
                "routing-keys", List.of(DOWNLINK_QUEUE))));

        deadLetterService.parkDeadMessage(message, DOWNLINK_DLQ, "DEAD_LETTER");

        ArgumentCaptor<CmMqDeadLetter> captor = ArgumentCaptor.forClass(CmMqDeadLetter.class);
        verify(deadLetterMapper).insert(captor.capture());
        assertEquals("cm.command", captor.getValue().getExchangeName());
        assertEquals(DOWNLINK_QUEUE, captor.getValue().getRoutingKey());
        assertEquals("cmd-0001", captor.getValue().getCommandId());
    }

    @Test
    @DisplayName("应答死信不把事件 ID 写成 commandId")
    void should_not_write_command_id_for_ack_dead_letter() {
        Message message = message("15880022239-7-1730000000123-1", List.of(Map.of(
                "exchange", "cm.command",
                "routing-keys", List.of("command.ack"))));

        deadLetterService.parkDeadMessage(message, ACK_DLQ, "DEAD_LETTER");

        ArgumentCaptor<CmMqDeadLetter> captor = ArgumentCaptor.forClass(CmMqDeadLetter.class);
        verify(deadLetterMapper).insert(captor.capture());
        assertEquals("", captor.getValue().getCommandId());
        assertEquals("command.ack", captor.getValue().getRoutingKey());
    }

    @Test
    @DisplayName("没有 x-death 时路由留空，重放直接拒绝，不消耗重放机会")
    void should_refuse_replay_without_original_route() {
        CmMqDeadLetter letter = new CmMqDeadLetter();
        letter.setId(1L);
        letter.setMessageId("cmd-0002");
        letter.setSourceQueue(DOWNLINK_DLQ);
        letter.setExchangeName("");
        letter.setRoutingKey("");
        letter.setDeadStatus("PENDING");
        when(deadLetterMapper.selectById(1L)).thenReturn(letter);

        assertFalse(deadLetterService.replay(1L));

        verify(deadLetterMapper, never()).markReplayed(any(), any());
        verify(publisherProvider, never()).getObject();
    }

    private Message message(String messageId, List<Map<String, Object>> xDeath) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setHeader("x-death", xDeath);
        return new Message("{\"eventType\":\"COMMAND_DOWNLINK\"}".getBytes(StandardCharsets.UTF_8), properties);
    }
} 
