package com.cm.gateway.downlink;

import com.cm.gateway.downlink.CommandFrameEncoder.EncodedCommand;
import com.cm.gateway.downlink.TerminalChannelRegistry.TerminalConnection;
import com.cm.infrastructure.command.AttemptStatus;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.CommandDownlinkEvent;
import com.cm.infrastructure.command.CommandJson;
import com.cm.infrastructure.command.CommandStatus;
import com.cm.infrastructure.persistence.entity.CmCommandAttempt;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.service.CmCommandAttemptService;
import io.netty.channel.ChannelFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * command.downlink.{gatewayId} 消费者。
 * 顺序固定：校验指令仍待发 → 找本地连接 → 分配流水号 → 落尝试映射 → writeAndFlush → 写成功才置已发终端。
 * 同一 gatewayId 的下行串行消费（concurrency=1），保证同一连接上的写序与流水号分配不被打乱。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandDownlinkConsumer {

    private static final int WRITE_TIMEOUT_MILLIS = 3000;

    private static final String REASON_NO_CONNECTION = "NO_CONNECTION";

    private static final String REASON_UNSUPPORTED = "UNSUPPORTED_COMMAND";

    private static final String REASON_WRITE_FAILED = "WRITE_FAILED";

    private final TerminalChannelRegistry channelRegistry;

    private final CommandFrameEncoder frameEncoder;

    private final CmCommandAttemptService attemptService;

    private final CmTerminalCommandMapper terminalCommandMapper;

    private final CmCommandProperties properties;

    @RabbitListener(queues = "${cm.command.mq.downlink-queue:command.downlink.gw-1}", concurrency = "1")
    public void onMessage(Message message) {
        CommandDownlinkEvent event = parse(message);
        CmTerminalCommand command = terminalCommandMapper.selectByCommandId(event.commandId());
        if (command == null) {
            log.error("下行事件对应的指令不存在，转入死信, commandId={}", event.commandId());
            throw new AmqpRejectAndDontRequeueException("指令不存在: " + event.commandId());
        }
        if (!CommandStatus.ACCEPTED.equals(command.getCommandStatus())
                && !CommandStatus.SENT_MQ.equals(command.getCommandStatus())) {
            log.info("指令已超出待发状态，忽略重复下行, commandId={}, status={}",
                    command.getCommandId(), command.getCommandStatus());
            return;
        }
        Optional<TerminalConnection> found = channelRegistry.find(event.simNo());
        if (found.isEmpty() || !found.get().active()) {
            // Outbox 已 confirm，不会再有同一消息的投递；这里必须给指令一个明确终态，否则永远停在已发 MQ。
            terminalCommandMapper.markRejected(command.getCommandId(), REASON_NO_CONNECTION);
            log.warn("目标终端无可用连接，指令置失败, commandId={}, simNo={}",
                    command.getCommandId(), event.simNo());
            return;
        }
        writeToTerminal(command, found.get());
    }

    private void writeToTerminal(CmTerminalCommand command, TerminalConnection target) {
        String simNo = command.getSimNo();
        long incarnation = target.route().incarnation();
        int serialNo = target.nextSerialNo();
        Optional<EncodedCommand> encoded = frameEncoder.encode(command, simNo, serialNo);
        if (encoded.isEmpty()) {
            terminalCommandMapper.markRejected(command.getCommandId(), REASON_UNSUPPORTED);
            log.warn("指令编码未注册，直接置失败, commandId={}, commandCode={}",
                    command.getCommandId(), command.getCommandCode());
            return;
        }
        EncodedCommand frame = encoded.get();
        CmCommandAttempt attempt;
        try {
            attempt = attemptService.register(command.getCommandId(), simNo, serialNo, incarnation,
                    target.route().gatewayId(), frame.downlinkMessageId(), frame.expectedAckMessageId());
        } catch (DuplicateKeyException ex) {
            log.info("同一尝试已落库，跳过重复投递, commandId={}, simNo={}, serialNo={}",
                    command.getCommandId(), simNo, serialNo);
            return;
        }
        ChannelFuture future = target.channel().writeAndFlush(frame.frame());
        boolean written = future.awaitUninterruptibly(WRITE_TIMEOUT_MILLIS) && future.isSuccess();
        if (!written) {
            attemptService.markWriteFailure(attempt.getId(), AttemptStatus.FAILED, REASON_WRITE_FAILED);
            terminalCommandMapper.markRejected(command.getCommandId(), REASON_WRITE_FAILED);
            log.warn("指令写入 Socket 失败，指令置失败, commandId={}, simNo={}, serialNo={}",
                    command.getCommandId(), simNo, serialNo);
            return;
        }
        attemptService.markWritten(attempt.getId());
        LocalDateTime sentAt = LocalDateTime.now();
        terminalCommandMapper.markSentTerminal(command.getCommandId(), attempt.getAttemptNo(), serialNo,
                sentAt, sentAt.plusSeconds(properties.getAckTimeoutSeconds()));
        log.info("指令已写入终端, commandId={}, simNo={}, attemptNo={}, serialNo={}",
                command.getCommandId(), simNo, attempt.getAttemptNo(), serialNo);
    }

    private CommandDownlinkEvent parse(Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            return CommandJson.read(json, CommandDownlinkEvent.class);
        } catch (RuntimeException ex) {
            log.error("指令下行事件无法解析，转入死信, messageId={}",
                    message.getMessageProperties().getMessageId(), ex);
            throw new AmqpRejectAndDontRequeueException("指令下行事件无法解析", ex);
        }
    }
}
