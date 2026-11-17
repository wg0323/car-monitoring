package com.cm.gov.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cm.gov.CmGov809Application;
import com.cm.gov.command.GovUrgeTodoSender;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Frame;
import com.cm.protocol.jt809.Jt809FrameCodec;
import com.cm.protocol.jt809.body.MainLinkLogin;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import com.cm.protocol.jt809.body.SubLinkLogin;
import com.cm.protocol.jt809.body.SubLinkLoginAck;
import com.cm.protocol.jt809.body.UrgeTodoRequest;
import com.cm.protocol.jt809.body.VehicleLocation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 监管侧 809 端到端：下级用阻塞 Socket 模拟，验证 19100 登录、回连从链路、
 * 位置计数与督办下发。默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = CmGov809Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "server.port=0",
                "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true&useSSL=false}",
                "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
                "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
                "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
                "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
                "mybatis-plus.global-config.db-config.id-type=assign_id",
                "cm.gov809.server.listen-address=127.0.0.1",
                "cm.gov809.server.heartbeat-interval-seconds=30"
        })
class GovSuperiorLinkTest {

    private static final long LOWER_PLATFORM_ID = 9001L;

    private static final long LOWER_USER_ID = 9001L;

    private static final String LOWER_PASSWORD = "12345678";

    private static final String PLATE_NO = "测A00001";

    private static final long SUPERVISE_ID = 2147483646L;

    private static final int LISTEN_PORT = freePort();

    private static final int LOWER_SUB_LINK_PORT = freePort();

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("cm.gov809.server.listen-port", () -> LISTEN_PORT);
    }

    @Autowired
    private GovUrgeTodoSender urgeTodoSender;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static LowerPlatform lower;

    @AfterEach
    void tearDown() {
        if (lower != null) {
            lower.close();
            lower = null;
        }
        jdbcTemplate.update("DELETE FROM cm_platform_info WHERE platform_id = ?", LOWER_PLATFORM_ID);
        jdbcTemplate.update("DELETE FROM cm_gov809_link WHERE platform_id = ?", LOWER_PLATFORM_ID);
        jdbcTemplate.update("DELETE FROM cm_jt809_command WHERE platform_id = ?", LOWER_PLATFORM_ID);
    }

    @AfterAll
    static void stopAll() {
        if (lower != null) {
            lower.close();
        }
    }

    @Test
    @DisplayName("下级登录、从链路回连、位置计数与督办下发")
    void should_accept_lower_platform_and_send_urge_todo() throws Exception {
        preparePlatformInfo();
        lower = new LowerPlatform(LISTEN_PORT, LOWER_SUB_LINK_PORT);
        lower.login(LOWER_USER_ID, LOWER_PASSWORD);

        Jt809Frame ack = lower.await(Jt809Constants.MSG_MAIN_LINK_LOGIN_ACK, 10_000L);
        assertEquals(MainLinkLoginAck.RESULT_SUCCESS, MainLinkLoginAck.parse(ack.body()).result());

        Jt809Frame subLogin = lower.acceptSubLinkAndAwaitLogin();
        assertEquals(LOWER_PLATFORM_ID, SubLinkLogin.parse(subLogin.body()).verifyCode());
        lower.replySub(Jt809Constants.MSG_SUB_LINK_LOGIN_ACK,
                new SubLinkLoginAck(SubLinkLoginAck.RESULT_SUCCESS).encode());
        awaitSubLinkOnline(10_000L);

        lower.sendMain(Jt809Constants.MSG_VEHICLE_LOCATION, locationBody());
        awaitPositionCount(1.0D, 10_000L);

        UrgeTodoRequest request = new UrgeTodoRequest(PLATE_NO, 2, 1, 2, 1763107200L,
                SUPERVISE_ID, 1763114400L, 1, "张三", "13800000000", "a@b.com");
        assertTrue(urgeTodoSender.sendUrgeTodo(LOWER_PLATFORM_ID, request));
        Jt809Frame urgeTodo = lower.await(Jt809Constants.MSG_WARN_URGE_TODO, 10_000L);
        UrgeTodoRequest received = UrgeTodoRequest.parse(urgeTodo.body());
        assertEquals(SUPERVISE_ID, received.superviseId());
        assertEquals(PLATE_NO, received.plateNo());
    }

    private void preparePlatformInfo() {
        jdbcTemplate.update("DELETE FROM cm_platform_info WHERE platform_id = ?", LOWER_PLATFORM_ID);
        jdbcTemplate.update("INSERT INTO cm_platform_info (id, platform_id, name, user_id, password,"
                        + " sublink_server_ip, sublink_port, upstream, is_enabled, create_time, update_time)"
                        + " VALUES (?, ?, '下级测试平台', ?, ?, '127.0.0.1', ?, 1, 1, NOW(3), NOW(3))",
                990901L, LOWER_PLATFORM_ID, String.valueOf(LOWER_USER_ID), LOWER_PASSWORD,
                LOWER_SUB_LINK_PORT);
    }

    private void awaitSubLinkOnline(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if ("LOGGED_IN".equals(jdbcTemplate.queryForObject(
                    "SELECT sub_link_state FROM cm_gov809_link WHERE platform_id = ? AND link_role = 'GOV_DOWNSTREAM'",
                    String.class, LOWER_PLATFORM_ID))) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("从链路未进入 LOGGED_IN");
    }

    private void awaitPositionCount(double expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Counter counter = meterRegistry.find("cm.gov809.received.positions").counter();
            if (counter != null && counter.count() >= expected) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("监管侧未收到定位计数");
    }

    private byte[] locationBody() {
        com.cm.protocol.jt809.body.LocationSample sample = new com.cm.protocol.jt809.body.LocationSample(0,
                java.time.LocalDateTime.of(2026, 11, 15, 12, 3, 21), 123456789, 30000000, 60, 62,
                1234L, 180, 35, 1L, 0L);
        return new VehicleLocation(PLATE_NO, 2, sample).encode();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("无法分配空闲端口", ex);
        }
    }

    /** 下级平台：连出主链路，同时在自己的从链路端口上等待监管侧回连。 */
    static final class LowerPlatform {

        private static final java.util.Map<Integer, ServerSocket> RESERVED = new java.util.concurrent.ConcurrentHashMap<>();

        private final BlockingQueue<Jt809Frame> frames = new LinkedBlockingQueue<>();

        private final int listenPort;

        private Socket mainSocket;

        private OutputStream mainOutput;

        private ServerSocket subServer;

        private Socket subSocket;

        private OutputStream subOutput;

        LowerPlatform(int listenPort, int subLinkPort) {
            this.listenPort = listenPort;
            try {
                this.subServer = new ServerSocket(subLinkPort);
            } catch (IOException ex) {
                throw new IllegalStateException("模拟下级从链路监听失败, port=" + subLinkPort, ex);
            }
        }

        void login(long userId, String password) throws IOException {
            mainSocket = new Socket("127.0.0.1", listenPort);
            mainOutput = mainSocket.getOutputStream();
            readFrames(mainSocket);
            sendMain(Jt809Constants.MSG_MAIN_LINK_LOGIN,
                    new MainLinkLogin(userId, password, "127.0.0.1", subServer.getLocalPort()).encode());
        }

        Jt809Frame acceptSubLinkAndAwaitLogin() throws IOException, InterruptedException {
            subSocket = subServer.accept();
            subOutput = subSocket.getOutputStream();
            readFrames(subSocket);
            return await(Jt809Constants.MSG_SUB_LINK_LOGIN, 10_000L);
        }

        void sendMain(int messageId, byte[] body) {
            write(mainOutput, messageId, body);
        }

        void replySub(int messageId, byte[] body) {
            write(subOutput, messageId, body);
        }

        Jt809Frame await(int messageId, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                Jt809Frame frame = frames.poll(100L, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                if (frame.messageId() == messageId) {
                    return frame;
                }
            }
            throw new AssertionError("未收到 0x" + Integer.toHexString(messageId));
        }

        void close() {
            closeQuietly(mainSocket);
            closeQuietly(subSocket);
            closeQuietly(subServer);
        }

        private void readFrames(Socket socket) {
            Thread reader = new Thread(() -> {
                try (InputStream input = socket.getInputStream()) {
                    while (!socket.isClosed()) {
                        frames.add(readFrame(input));
                    }
                } catch (IOException ex) {
                    // 连接关闭即退出。
                }
            }, "stub-lower-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private Jt809Frame readFrame(InputStream input) throws IOException {
            while (true) {
                int head = input.read();
                if (head < 0) {
                    throw new IOException("stream closed");
                }
                if (head != Jt809Constants.HEAD_FLAG) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                buffer.write(head);
                while (true) {
                    int value = input.read();
                    if (value < 0) {
                        throw new IOException("stream closed mid-frame");
                    }
                    buffer.write(value);
                    if (value == Jt809Constants.TAIL_FLAG) {
                        break;
                    }
                }
                try {
                    return Jt809FrameCodec.decode(buffer.toByteArray());
                } catch (IllegalArgumentException ex) {
                    // 噪声帧，继续找下一个帧头。
                }
            }
        }

        private void write(OutputStream output, int messageId, byte[] body) {
            try {
                output.write(Jt809FrameCodec.encode(messageId, 1L, LOWER_PLATFORM_ID, body));
                output.flush();
            } catch (IOException ex) {
                throw new IllegalStateException("模拟下级写入失败", ex);
            }
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            // 关闭异常不影响断言。
        }
    }
}
 
