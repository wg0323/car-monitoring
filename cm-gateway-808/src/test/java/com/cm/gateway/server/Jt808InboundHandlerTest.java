package com.cm.gateway.server;

import com.cm.domain.position.PositionTimePolicy;
import com.cm.domain.registration.RegistrationVerdict;
import com.cm.gateway.downlink.CommandAckPublisher;
import com.cm.gateway.downlink.TerminalChannelRegistry;
import com.cm.gateway.telemetry.PositionEventAssembler;
import com.cm.gateway.telemetry.PositionReportDispatcher;
import com.cm.infrastructure.command.CmCommandProperties;
import com.cm.infrastructure.command.TerminalRouteRegistry;
import com.cm.infrastructure.service.TerminalRegistrationService;
import com.cm.infrastructure.telemetry.OnlineLeaseStore;
import com.cm.infrastructure.telemetry.TelemetryMetrics;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import com.cm.protocol.FrameEscaper;
import com.cm.protocol.Jt808Encoder;
import com.cm.protocol.MessageHeader;
import com.cm.protocol.ProtocolBytes;
import com.cm.protocol.body.RegistrationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上行分发：注册回 0x8100、鉴权校验鉴权码、注销走同一条清理路径，
 * 定位帧交给发布编排并声明需要应答，批量定位按条拆分，心跳维护在线租约。
 */
class Jt808InboundHandlerTest {

    private static final String SIM_NO = "13800138000";

    private static final String GATEWAY_ID = "gw-test";

    private static final String AUTH_CODE = "ABC";

    private static final int SERIAL_NO = 7;

    private static final long INCARNATION = 1001L;

    private static final long VERIFY_TIMEOUT_MILLIS = 2000L;

    /** 否定断言的观察窗口：足够覆盖一次异步绑定任务。 */
    private static final long BIND_WINDOW_MILLIS = 300L;

    /** BCD 每一位必须是 0~9，2026-11-15 12:00:00。 */
    private static final byte[] BCD_TIME = new byte[] {0x26, 0x11, 0x15, 0x12, 0x00, 0x00};

    private TerminalChannelRegistry channelRegistry;

    private TerminalRouteRegistry routeRegistry;

    private OnlineLeaseStore onlineLeaseStore;

    private PositionReportDispatcher positionDispatcher;

    private TerminalRegistrationService registrationService;

    private ThreadPoolExecutor ackExecutor;

    private ThreadPoolExecutor registrationExecutor;

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channelRegistry = mock(TerminalChannelRegistry.class);
        when(channelRegistry.nextIncarnation()).thenReturn(INCARNATION);
        when(channelRegistry.unbind(any())).thenReturn(true);
        routeRegistry = mock(TerminalRouteRegistry.class);
        CommandAckPublisher ackPublisher = mock(CommandAckPublisher.class);
        onlineLeaseStore = mock(OnlineLeaseStore.class);
        positionDispatcher = mock(PositionReportDispatcher.class);
        registrationService = mock(TerminalRegistrationService.class);
        when(registrationService.evaluate(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(RegistrationVerdict.SUCCESS);
        ackExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        registrationExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        CmCommandProperties commandProperties = new CmCommandProperties();
        commandProperties.setGatewayId(GATEWAY_ID);
        GatewayProperties gatewayProperties = new GatewayProperties();
        gatewayProperties.setAuthCode(AUTH_CODE);
        PositionEventAssembler assembler = new PositionEventAssembler(
                new PositionTimePolicy(Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofMinutes(1),
                        ZoneId.of("Asia/Shanghai")),
                new TelemetryProperties(), commandProperties);
        Jt808InboundHandler handler = new Jt808InboundHandler(channelRegistry, routeRegistry, ackPublisher,
                commandProperties, ackExecutor, assembler, positionDispatcher, onlineLeaseStore,
                new TelemetryMetrics(new SimpleMeterRegistry()), gatewayProperties, registrationService,
                registrationExecutor);
        channel = new EmbeddedChannel(handler);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.close();
        ackExecutor.shutdown();
        ackExecutor.awaitTermination(1L, TimeUnit.SECONDS);
        registrationExecutor.shutdown();
        registrationExecutor.awaitTermination(1L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("单条定位交给发布编排并声明需要应答")
    void should_dispatch_single_position_with_ack() {
        channel.writeInbound(frame(0x0200, positionBody()));
        verify(positionDispatcher).dispatch(eq(channel), eq(SIM_NO), eq(0x0200), eq(SERIAL_NO),
                argThat(events -> events.size() == 1 && events.get(0).batchIndex() == 0
                        && "STANDARD_0200".equals(events.get(0).protocolBranch())),
                eq(true), any());
    }

    @Test
    @DisplayName("批量定位按条拆分且每条的 batchIndex 递增")
    void should_split_batch_position_into_events() {
        channel.writeInbound(frame(0x0704, batchBody(positionBody(), positionBody())));
        verify(positionDispatcher).dispatch(eq(channel), eq(SIM_NO), eq(0x0704), eq(SERIAL_NO),
                argThat(events -> events.size() == 2 && events.get(0).batchIndex() == 0
                        && events.get(1).batchIndex() == 1
                        && "BATCH_0704".equals(events.get(1).protocolBranch())),
                eq(true), any());
    }

    @Test
    @DisplayName("鉴权建立路由与在线租约，心跳续租")
    void should_register_lease_on_auth_and_renew_on_heartbeat() {
        channel.writeInbound(frame(0x0102, authBody()));
        verify(onlineLeaseStore, timeout(VERIFY_TIMEOUT_MILLIS))
                .register(eq(SIM_NO), eq(GATEWAY_ID), eq(INCARNATION));
        channel.writeInbound(frame(0x0002, new byte[0]));
        verify(onlineLeaseStore, timeout(VERIFY_TIMEOUT_MILLIS))
                .renew(eq(SIM_NO), eq(GATEWAY_ID), eq(INCARNATION));
    }

    @Test
    @DisplayName("断线按连接代号删除在线租约")
    void should_remove_lease_on_channel_close() {
        channel.writeInbound(frame(0x0102, authBody()));
        verify(onlineLeaseStore, timeout(VERIFY_TIMEOUT_MILLIS))
                .register(eq(SIM_NO), eq(GATEWAY_ID), eq(INCARNATION));
        channel.close();
        verify(onlineLeaseStore, timeout(VERIFY_TIMEOUT_MILLIS))
                .removeIfMatch(eq(SIM_NO), eq(GATEWAY_ID), eq(INCARNATION));
    }

    @Test
    @DisplayName("注册成功回 0x8100，结果 0 且带配置的鉴权码")
    void should_reply_registration_ack_with_auth_code() throws InterruptedException {
        channel.writeInbound(frame(0x0100, registrationBody()));

        RegistrationResponse response = registrationResponse(awaitOutbound());

        assertEquals(0, response.result());
        assertEquals(AUTH_CODE, response.authCode());
        assertEquals(SERIAL_NO, response.registerSerialNo());
    }

    @Test
    @DisplayName("车辆不存在时回 0x8100 结果 2，且不带鉴权码")
    void should_reply_failed_registration_without_auth_code() throws InterruptedException {
        when(registrationService.evaluate(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(RegistrationVerdict.VEHICLE_NOT_FOUND);

        channel.writeInbound(frame(0x0100, registrationBody()));

        RegistrationResponse response = registrationResponse(awaitOutbound());

        assertEquals(RegistrationVerdict.VEHICLE_NOT_FOUND.code(), response.result());
        assertEquals("", response.authCode());
    }

    @Test
    @DisplayName("鉴权码不匹配时不建立路由，回 0x8001 失败")
    void should_reject_authentication_with_wrong_code() throws InterruptedException {
        channel.writeInbound(frame(0x0102, "WRONG".getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, ackResult(awaitOutbound(), 0x0102));
        // 鉴权失败不该建立接入；给异步绑定任务留出窗口，不然这条断言可能先于任务执行而误报通过。
        verify(onlineLeaseStore, after(BIND_WINDOW_MILLIS).times(0))
                .register(anyString(), anyString(), anyLong());
        verify(channelRegistry, after(BIND_WINDOW_MILLIS).times(0)).bind(any(), any());
    }

    @Test
    @DisplayName("注销后清理路由与在线租约")
    void should_clean_up_on_deregistration() throws InterruptedException {
        channel.writeInbound(frame(0x0102, authBody()));
        verify(onlineLeaseStore, timeout(VERIFY_TIMEOUT_MILLIS))
                .register(eq(SIM_NO), eq(GATEWAY_ID), eq(INCARNATION));
        drainOutbound();

        channel.writeInbound(frame(0x0003, new byte[0]));

        assertEquals(0, ackResult(awaitOutbound(), 0x0003));
        verify(onlineLeaseStore, timeout(VERIFY_TIMEOUT_MILLIS))
                .removeIfMatch(eq(SIM_NO), eq(GATEWAY_ID), eq(INCARNATION));
    }

    private byte[] authBody() {
        return AUTH_CODE.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] registrationBody() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtocolBytes.writeShort(out, 11);
        ProtocolBytes.writeShort(out, 4);
        out.write(new byte[] {'M', '1', '2', '3', '4'}, 0, 5);
        byte[] model = "AB123456".getBytes(StandardCharsets.US_ASCII);
        out.write(model, 0, model.length);
        byte[] terminalId = "A112233".getBytes(StandardCharsets.US_ASCII);
        out.write(terminalId, 0, terminalId.length);
        ProtocolBytes.writeByte(out, 2);
        byte[] plate = "甘A43060".getBytes(java.nio.charset.Charset.forName("GBK"));
        out.write(plate, 0, plate.length);
        return out.toByteArray();
    }

    private void drainOutbound() {
        while (channel.readOutbound() != null) {
            // 丢弃之前用例里已写出的帧，避免误把上一条应答当成下一条。
        }
    }

    private byte[] awaitOutbound() throws InterruptedException {
        long deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Object outbound = channel.readOutbound();
            if (outbound instanceof byte[] frame) {
                return frame;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("未在超时前收到下行帧");
    }

    private static byte[] inner(byte[] frame) {
        return FrameEscaper.unescape(FrameEscaper.stripDelimiters(frame));
    }

    private static RegistrationResponse registrationResponse(byte[] frame) {
        byte[] inner = inner(frame);
        MessageHeader header = MessageHeader.parse(inner, 0);
        assertEquals(RegistrationResponse.MESSAGE_ID, header.messageId());
        return RegistrationResponse.parse(inner, header.length(), header.bodySize());
    }

    private static int ackResult(byte[] frame, int ackedMessageId) {
        byte[] inner = inner(frame);
        MessageHeader header = MessageHeader.parse(inner, 0);
        assertEquals(0x8001, header.messageId());
        int bodyOffset = header.length();
        assertEquals(ackedMessageId, ProtocolBytes.unsignedShort(inner, bodyOffset + 2));
        return ProtocolBytes.unsignedByte(inner, bodyOffset + 4);
    }

    private byte[] frame(int messageId, byte[] body) {
        MessageHeader header = MessageHeader.forDownlink(messageId, SIM_NO, SERIAL_NO, body.length);
        return Jt808Encoder.encode(header, body);
    }

    private byte[] positionBody() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, 0);
        writeInt(out, 1);
        writeInt(out, 30000000);
        writeInt(out, 120000000);
        ProtocolBytes.writeShort(out, 0);
        ProtocolBytes.writeShort(out, 60);
        ProtocolBytes.writeShort(out, 90);
        out.write(BCD_TIME, 0, BCD_TIME.length);
        return out.toByteArray();
    }

    private byte[] batchBody(byte[]... items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtocolBytes.writeShort(out, items.length);
        ProtocolBytes.writeByte(out, 0);
        for (byte[] item : items) {
            ProtocolBytes.writeShort(out, item.length);
            out.write(item, 0, item.length);
        }
        return out.toByteArray();
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24 & 0xFF);
        out.write(value >>> 16 & 0xFF);
        out.write(value >>> 8 & 0xFF);
        out.write(value & 0xFF);
    }
}
