package com.cm.gateway.downlink;

import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.protocol.Jt808Encoder;
import com.cm.protocol.MessageHeader;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 指令报文编码注册表。
 * 本轮只注册 0x8201 位置信息查询：它没有消息体，对端 0x0201 能闭合“业务应答即成功”。
 * 其余指令编码没有协议实现，显式返回空，由调用方置失败，不用默认值兜底下发。
 */
@Component
public class CommandFrameEncoder {

    private static final int POSITION_QUERY_MESSAGE_ID = 0x8201;

    private static final int EXPECTED_ACK_POSITION_QUERY = 0x0201;

    private static final Set<String> POSITION_QUERY_CODES = Set.of("0x8201", "8201");

    public Optional<EncodedCommand> encode(CmTerminalCommand command, String simNo, int serialNo) {
        String code = command.getCommandCode() == null ? "" : command.getCommandCode().trim().toLowerCase(Locale.ROOT);
        if (!POSITION_QUERY_CODES.contains(code)) {
            return Optional.empty();
        }
        byte[] frame = Jt808Encoder.encode(
                MessageHeader.forDownlink(POSITION_QUERY_MESSAGE_ID, simNo, serialNo, 0), null);
        return Optional.of(new EncodedCommand(frame, POSITION_QUERY_MESSAGE_ID, EXPECTED_ACK_POSITION_QUERY));
    }

    /**
     * 编码结果：下行消息 ID 用于留痕，期望应答 ID 用于 ACK 归属校验。
     */
    public record EncodedCommand(byte[] frame, int downlinkMessageId, int expectedAckMessageId) {
    }
}
