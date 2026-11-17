package com.cm.gateway.server;

import com.cm.gateway.downlink.CommandAckPublisher;
import com.cm.gateway.downlink.TerminalChannelRegistry;
import com.cm.gateway.telemetry.PositionEventAssembler;
import com.cm.gateway.telemetry.PositionReportDispatcher;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.CommandAckEvent;
import com.cm.infrastructure.command.TerminalRouteRegistry;
import com.cm.infrastructure.service.TerminalRegistrationService;
import com.cm.infrastructure.telemetry.OnlineLeaseStore;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.infrastructure.telemetry.TelemetryPublishTracker;
import com.cm.infrastructure.telemetry.TelemetryPublisher;
import com.cm.domain.position.PositionTimePolicy;
import com.cm.domain.registration.RegistrationVerdict;
import com.cm.protocol.FrameChecksum;
import com.cm.protocol.FrameEscaper;
import com.cm.protocol.Jt808Decoder;
import com.cm.protocol.Jt808Encoder;
import com.cm.protocol.Jt808Message;
import com.cm.protocol.MessageHeader;
import com.cm.protocol.ProtocolBytes;
import com.cm.protocol.body.RegistrationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实的 Netty 监听 + 真实 Socket 客户端的端到端用例：注册拿 0x8100 与鉴权码，
 * 用该码鉴权拿 0x8001，再上一条 0x0200 拿对应流水号的 0x8001。
 * MQ、Redis 与查库判定用 mock，不连外部中间件。
 */
class Jt808TcpEndToEndTest {

    private static final String SIM_NO = "13912345678";

    private static final String AUTH_CODE = "1234567890A";

    private static final int MSG_REGISTER = 0x0100;

    private static final int MSG_AUTH = 0x0102;

    private static final int MSG_POSITION = 0x0200;

    private static final int MSG_POSITION_QUERY_ACK = 0x0201;

    /** 下行点名。它没有消息体，对端的 0x0201 就是业务应答。 */
    private static final int MSG_POSITION_QUERY = 0x8201;

    private static final int MSG_PLATFORM_ACK = 0x8001;

    private static final int REGISTER_SERIAL = 6;

    private static final int AUTH_SERIAL = 7;

    private static final int POSITION_SERIAL = 8;

    private static final int READ_TIMEOUT_MILLIS = 5000;

    private static final byte FRAME_FLAG = 0x7e;

    private ThreadPoolExecutor executor;

    private Jt808Server server;

    private Socket socket;

    private GatewayProperties gatewayProperties;

    /** 真实连接表：下发 0x8201 要找得到鉴权后绑定的 Channel。 */
    private TerminalChannelRegistry channelRegistry;

    private CommandAckPublisher ackPublisher;

    @BeforeEach
    void setUp() throws IOException {
        gatewayProperties = new GatewayProperties();
        // tcp-port <= 0 表示不监听，所以先探一个空闲端再交给服务端绑定。
        gatewayProperties.setTcpPort(freePort());
        gatewayProperties.setAuthCode(AUTH_CODE);
        channelRegistry = new TerminalChannelRegistry();
        ackPublisher = mock(CommandAckPublisher.class);
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "cm-test-publish"));
        server = new Jt808Server(gatewayProperties, channelInitializer());
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (socket != null) {
            socket.close();
        }
        server.stop();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("假终端鉴权与定位上报都能收到 0x8001 平台应答")
    void should_ack_auth_and_position_over_real_socket() throws Exception {
        byte[] auth = authFrame();
        byte[] position = positionFrame();
        assertDecodable(auth);
        assertDecodable(position);

        connect();
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(auth);
        out.flush();
        readAck(in, AUTH_SERIAL, MSG_AUTH);

        out.write(position);
        out.flush();
        readAck(in, POSITION_SERIAL, MSG_POSITION);
    }

    @Test
    @DisplayName("注册拿 0x8100 与鉴权码，用该码鉴权成功")
    void should_register_then_authenticate_with_issued_code() throws Exception {
        byte[] registration = registrationFrame();
        assertDecodable(registration);

        connect();
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        out.write(registration);
        out.flush();
        RegistrationResponse response = readRegistrationAck(in);

        assertEquals(RegistrationResponse.RESULT_SUCCESS, response.result());
        assertEquals(AUTH_CODE, response.authCode());
        assertEquals(REGISTER_SERIAL, response.registerSerialNo());

        out.write(authFrame());
        out.flush();
        readAck(in, AUTH_SERIAL, MSG_AUTH);
    }

    /**
     * TC-DAT-04 的网关侧链路：鉴权后网关绑定连接 → 下行 0x8201 写到 Socket →
     * 终端回 0x0201 → 网关把 (simNo, 流水号, incarnation) 与结果发到 command.ack。
     */
    @Test
    @DisplayName("下发 0x8201 后终端回 0x0201，应答以同一流水号上报")
    void should_deliver_position_query_and_publish_ack() throws Exception {
        connect();
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        out.write(authFrame());
        out.flush();
        readAck(in, AUTH_SERIAL, MSG_AUTH);

        TerminalChannelRegistry.TerminalConnection connection = channelRegistry.find(SIM_NO)
                .orElseThrow(() -> new IllegalStateException("鉴权后应已绑定连接"));
        int serialNo = connection.nextSerialNo();
        byte[] downlink = Jt808Encoder.encode(
                MessageHeader.forDownlink(MSG_POSITION_QUERY, SIM_NO, serialNo, 0), new byte[0]);
        connection.channel().writeAndFlush(downlink).sync();

        byte[] received = readFrame(in);
        assertEquals(MSG_POSITION_QUERY, ProtocolBytes.unsignedShort(received, 0));
        // 普通消息头的流水号在偏移 10；偏移 12 是 0x8001 通用应答里的「应答流水号」位置。
        assertEquals(serialNo, ProtocolBytes.unsignedShort(received, 10));

        out.write(positionQueryAckFrame(serialNo));
        out.flush();

        ArgumentCaptor<CommandAckEvent> captor = ArgumentCaptor.forClass(CommandAckEvent.class);
        verify(ackPublisher, timeout(3000)).publish(captor.capture());
        CommandAckEvent event = captor.getValue();
        assertEquals(SIM_NO, event.simNo());
        assertEquals(serialNo, event.terminalSerialNo());
        assertEquals(MSG_POSITION_QUERY_ACK, event.ackMessageId());
        assertEquals(0, event.ackResult(), "0x0201 到达即视为业务成功");
    }

    private void connect() throws IOException {
        assertTrue(server.port() > 0, "监听端口应已分配");
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", server.port()), READ_TIMEOUT_MILLIS);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
    }

    /**
     * legacy 长度的注册体：型号 8 字节，车牌无颜色以外的填充，长度 33 < 36 走旧分支。
     */
    private static byte[] registrationFrame() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ProtocolBytes.writeShort(body, 11);
        ProtocolBytes.writeShort(body, 4);
        body.write(new byte[] {'M', '1', '2', '3', '4'}, 0, 5);
        byte[] model = "AB123456".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        body.write(model, 0, model.length);
        byte[] terminalId = "A112233".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        body.write(terminalId, 0, terminalId.length);
        ProtocolBytes.writeByte(body, 2);
        byte[] plate = "甘A43060".getBytes(java.nio.charset.Charset.forName("GBK"));
        body.write(plate, 0, plate.length);
        return frame(MSG_REGISTER, REGISTER_SERIAL, body.toByteArray());
    }

    /**
     * 先在本地自检一遍：构造的帧必须能被同一个上行解码器解开，否则对端报解析失败时无法区分是测试帧还是服务端的问题。
     */
    private static void assertDecodable(byte[] frame) {
        Jt808Message message = Jt808Decoder.decode(frame);
        assertTrue(!message.failed(), "测试帧应可解码: " + message.errorMessage()
                + ", hex=" + java.util.HexFormat.of().formatHex(frame));
        assertEquals(MessageHeader.parse(unescaped(frame), 0).messageId(), message.header().messageId());
    }

    private static byte[] unescaped(byte[] frame) {
        return FrameEscaper.unescape(FrameEscaper.stripDelimiters(frame));
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private Jt808ChannelInitializer channelInitializer() {
        CmCommandProperties commandProperties = new CmCommandProperties();
        TelemetryProperties telemetryProperties = new TelemetryProperties();
        PositionTimePolicy timePolicy = new PositionTimePolicy(Duration.ofSeconds(300),
                Duration.ofSeconds(86400), Duration.ofSeconds(60), ZoneId.of("Asia/Shanghai"));
        TelemetryMetrics metrics = new TelemetryMetrics(new SimpleMeterRegistry());

        TelemetryPublisher publisher = mock(TelemetryPublisher.class);
        when(publisher.publishBatch(any())).thenAnswer(invocation -> {
            TelemetryPublishTracker tracker = TelemetryPublishTracker.of(1);
            tracker.onAck();
            return tracker;
        });

        PositionEventAssembler assembler = new PositionEventAssembler(timePolicy, telemetryProperties,
                commandProperties);
        PositionReportDispatcher dispatcher = new PositionReportDispatcher(publisher, telemetryProperties,
                metrics, executor);

        TerminalRegistrationService registrationService = mock(TerminalRegistrationService.class);
        when(registrationService.evaluate(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(RegistrationVerdict.SUCCESS);

        return new Jt808ChannelInitializer(channelRegistry, mock(TerminalRouteRegistry.class),
                ackPublisher, commandProperties, executor, assembler, dispatcher,
                mock(OnlineLeaseStore.class), metrics, gatewayProperties, registrationService, executor);
    }

    /**
     * 鉴权码是裸字节，没有长度前缀：黄金报文 UL-0102-01 的消息体就是 11 字节的码本身。
     */
    private static byte[] authFrame() {
        return frame(MSG_AUTH, AUTH_SERIAL, AUTH_CODE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] positionFrame() throws IOException {
        return frame(MSG_POSITION, POSITION_SERIAL, positionBody());
    }

    /** 0x0201 的消息体 = 应答流水号 + 一整段位置信息（与 0x0200 同布局）。 */
    private static byte[] positionQueryAckFrame(int responseSerialNo) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ProtocolBytes.writeShort(body, responseSerialNo);
        body.write(positionBody());
        return frame(MSG_POSITION_QUERY_ACK, POSITION_SERIAL + 1, body.toByteArray());
    }

    private static byte[] positionBody() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        data.writeInt(0);
        data.writeInt(1);
        data.writeInt(30200000);
        data.writeInt(120000000);
        data.writeShort(100);
        data.writeShort(600);
        data.writeShort(90);
        data.write(new byte[] {0x26, 0x11, 0x15, 0x10, 0x00, 0x00});
        data.writeByte(0x01);
        data.writeByte(0x04);
        data.writeInt(12000);
        data.flush();
        return body.toByteArray();
    }

    private static byte[] frame(int messageId, int serialNo, byte[] body) {
        return Jt808Encoder.encode(MessageHeader.forDownlink(messageId, SIM_NO, serialNo, body.length), body);
    }

    /**
     * 从流里切出一帧并返回去标识位、去转义后的帧内字节：0x7e 是帧首尾标识，
     * 转义后的 0x7d 0x02 不会出现 0x7e 字节，所以直接扫边界即可。
     */
    private static byte[] readFrame(InputStream in) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        int current = in.read();
        while (current >= 0 && current != FRAME_FLAG) {
            current = in.read();
        }
        if (current < 0) {
            throw new IllegalStateException("连接已关闭，未读到帧首标识");
        }
        frame.write(FRAME_FLAG);
        while (true) {
            current = in.read();
            if (current < 0) {
                throw new IllegalStateException("连接已关闭，未读到帧尾标识");
            }
            frame.write(current);
            if (current == FRAME_FLAG) {
                break;
            }
        }
        byte[] inner = FrameEscaper.unescape(FrameEscaper.stripDelimiters(frame.toByteArray()));
        int contentLength = inner.length - 1;
        assertTrue(FrameChecksum.matches(inner, 0, contentLength, inner[contentLength]), "下行帧校验码应正确");
        return inner;
    }

    /**
     * 0x8001 是下行报文，cm-protocol 的上行解码器不解析它，所以这里直接断言帧头与消息体。
     */
    private static void readAck(InputStream in, int expectedSerialNo, int expectedMessageId) throws IOException {
        byte[] inner = readFrame(in);
        assertEquals(MSG_PLATFORM_ACK, ProtocolBytes.unsignedShort(inner, 0));
        assertEquals(expectedSerialNo, ProtocolBytes.unsignedShort(inner, 12));
        assertEquals(expectedMessageId, ProtocolBytes.unsignedShort(inner, 14));
        assertEquals(0, ProtocolBytes.unsignedByte(inner, 16));
    }

    private static RegistrationResponse readRegistrationAck(InputStream in) throws IOException {
        byte[] inner = readFrame(in);
        MessageHeader header = MessageHeader.parse(inner, 0);
        assertEquals(RegistrationResponse.MESSAGE_ID, header.messageId());
        return RegistrationResponse.parse(inner, header.length(), header.bodySize());
    }
}
