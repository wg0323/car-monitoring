package com.cm.gateway.downlink;

import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.protocol.Jt808Decoder;
import com.cm.protocol.Jt808Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下行编码注册表：本轮只认 0x8201，未注册的编码显式失败而不是猜消息体。
 */
class CommandFrameEncoderTest {

    private static final String SIM_NO = "15880022239";

    private final CommandFrameEncoder encoder = new CommandFrameEncoder();

    @Test
    @DisplayName("0x8201 编码出的帧能被协议层解析回同样的手机号与流水号")
    void should_encode_position_query_frame() {
        Optional<CommandFrameEncoder.EncodedCommand> encoded = encoder.encode(command("0x8201"), SIM_NO, 12);

        assertTrue(encoded.isPresent());
        assertEquals(0x8201, encoded.get().downlinkMessageId());
        assertEquals(0x0201, encoded.get().expectedAckMessageId());

        Jt808Message decoded = Jt808Decoder.decode(encoded.get().frame());
        assertFalse(decoded.failed());
        assertEquals(0x8201, decoded.header().messageId());
        assertEquals(SIM_NO, decoded.header().simNo());
        assertEquals(12, decoded.header().serialNo());
        assertEquals(0, decoded.header().bodySize());
    }

    @Test
    @DisplayName("不带 0x 前缀的指令编码同样可识别")
    void should_accept_code_without_prefix() {
        assertTrue(encoder.encode(command("8201"), SIM_NO, 1).isPresent());
    }

    @Test
    @DisplayName("未注册的指令编码返回空，由调用方置失败")
    void should_return_empty_for_unregistered_code() {
        assertTrue(encoder.encode(command("0x8103"), SIM_NO, 1).isEmpty());
        assertTrue(encoder.encode(command(""), SIM_NO, 1).isEmpty());
        assertTrue(encoder.encode(command(null), SIM_NO, 1).isEmpty());
    }

    private CmTerminalCommand command(String commandCode) {
        CmTerminalCommand command = new CmTerminalCommand();
        command.setCommandCode(commandCode);
        return command;
    }
}
