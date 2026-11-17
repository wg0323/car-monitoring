package com.cm.integration.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;
import com.cm.infrastructure.telemetry.TelemetryJson;
import com.cm.integration.CmIntegration809Application;
import com.cm.integration.command.Gov809CommandService;
import com.cm.integration.forward.Gov809ForwardHandler;
import com.cm.protocol.jt809.Jt809Bytes;
import com.cm.protocol.jt809.Jt809Constants;
import com.cm.protocol.jt809.Jt809Frame;
import com.cm.protocol.jt809.Jt809FrameCodec;
import com.cm.protocol.jt809.body.LocationSample;
import com.cm.protocol.jt809.body.MainLinkLogin;
import com.cm.protocol.jt809.body.MainLinkLoginAck;
import com.cm.protocol.jt809.body.SubLinkLogin;
import com.cm.protocol.jt809.body.SubLinkLoginAck;
import com.cm.protocol.jt809.body.UrgeTodoAck;
import com.cm.protocol.jt809.body.UrgeTodoRequest;
import com.cm.protocol.jt809.body.VehicleLocation;
import com.cm.protocol.jt809.body.VehicleLocationBatch;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 企业侧 809 链路端到端：真实 Netty 客户端与 12345 监听，对端用阻塞 Socket 模拟上级平台。
 * 覆盖登录-从链路-位置转发-断链积压-重连补发-督办落库与应答全链路。
 * 默认不跑，设置 CM_P3_DB_TEST=true 启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P3_DB_TEST", matches = "true")
@SpringBootTest(classes = CmIntegration809Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
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
                "cm.gov809.reconnect-initial-millis=200",
                "cm.gov809.reconnect-max-millis=1000",
                "cm.gov809.heartbeat-interval-seconds=30",
                "cm.gov809.checkpoint-flush-millis=300",
                "cm.gov809.mq.consume-enabled=false",
                "cm.gov809.mq.declare-enabled=false"
        })
class Gov809LinkEndToEndTest {

    private static final long PLATFORM_ID = 8992L;

    private static final String SIM_NO = "13900008092";

    private static final String PLATE_NO = "测S30001";

    private static final int PLATE_COLOR = 2;

    private static final long SUPERVISE_ID = 2147483646L;

    private static final int UPSTREAM_PORT = freePort();

    private static final int SUB_LINK_PORT = freePort();

    private static final StubSuperior SUPERIOR = new StubSuperior(UPSTREAM_PORT);

    static {
        SUPERIOR.start();
    }

    @DynamicPropertySource
    static void gov809Properties(DynamicPropertyRegistry registry) {
        registry.add("cm.gov809.upstream-port", () -> UPSTREAM_PORT);
        registry.add("cm.gov809.sub-link-port", () -> SUB_LINK_PORT);
        registry.add("cm.gov809.sub-link-advertise-ip", () -> "127.0.0.1");
        registry.add("cm.gov809.sub-link-bind-address", () -> "127.0.0.1");
    }

    @Autowired
    private Gov809LinkManager linkManager;

    @Autowired
    private Gov809ForwardHandler forwardHandler;

    @Autowired
    private Gov809CommandService commandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanBusinessRows() {
        jdbcTemplate.update("DELETE FROM cm_gov809_outbox WHERE platform_id = ?", PLATFORM_ID);
        jdbcTemplate.update("DELETE FROM cm_warn_msg_urge_todo_req WHERE platform_id = ?", PLATFORM_ID);
        jdbcTemplate.update("DELETE FROM cm_vehicle WHERE sim_no = ? OR plate_no = ?", SIM_NO, PLATE_NO);
    }

    @AfterAll
    static void stopStub() {
        SUPERIOR.stop();
    }

    @Test
    @DisplayName("登录、从链路、位置转发、断链补发与督办闭环")
    void should_close_the_loop_over_real_tcp() throws Exception {
        long deadline = System.currentTimeMillis() + 20_000L;
        assertTrue(awaitLogin(deadline), "主链路未在超时内登录成功");
        SUPERIOR.connectSubLink();
        awaitFrame(Jt809Constants.MSG_SUB_LINK_LOGIN_ACK, frame -> true, 10_000L);
        awaitUsableLink(10_000L);

        prepareVehicle();
        forwardHandler.handle(TelemetryJson.write(event("evt-live-1", "2026-11-15T04:03:21Z")));
        Jt809Frame live = awaitFrame(Jt809Constants.MSG_VEHICLE_LOCATION,
                frame -> VehicleLocation.parse(frame.body()).sample() != null, 10_000L);
        VehicleLocation liveLocation = VehicleLocation.parse(live.body());
        assertEquals(PLATE_NO, liveLocation.plateNo());
        assertEquals(PLATE_COLOR, liveLocation.plateColor());
        assertEquals(60, liveLocation.sample().speed());

        // 断链：关闭模拟上级的监听与连接，客户端进入重连退避。
        SUPERIOR.closeAll();
        awaitLinkDown(20_000L);
        forwardHandler.handle(TelemetryJson.write(event("evt-buffered-1", "2026-11-15T04:04:00Z")));
        forwardHandler.handle(TelemetryJson.write(event("evt-buffered-2", "2026-11-15T04:04:30Z")));
        assertEquals(2L, pendingOutbox());

        SUPERIOR.restart();
        awaitLogin(System.currentTimeMillis() + 30_000L);
        SUPERIOR.connectSubLink();
        awaitFrame(Jt809Constants.MSG_SUB_LINK_LOGIN_ACK, frame -> true, 10_000L);
        Jt809Frame replay = awaitFrame(Jt809Constants.MSG_VEHICLE_LOCATION,
                frame -> subType(frame.body()) == Jt809Constants.SUB_TYPE_HISTORY_LOCATION,
                30_000L);
        VehicleLocationBatch batch = VehicleLocationBatch.parse(replay.body());
        assertEquals(2, batch.samples().size());
        awaitPending(0L, 10_000L);

        SUPERIOR.sendUrgeTodo(urgeTodoRequest());
        awaitUrgeTodoRow();
        assertTrue(commandService.sendUrgeTodoAck(PLATE_NO, PLATE_COLOR, SUPERVISE_ID, 1));
        Jt809Frame ack = awaitFrame(Jt809Constants.MSG_WARN_URGE_TODO,
                frame -> subType(frame.body()) == Jt809Constants.SUB_TYPE_URGE_TODO_ACK,
                10_000L);
        UrgeTodoAck urgeTodoAck = UrgeTodoAck.parse(ack.body());
        assertEquals(SUPERVISE_ID, urgeTodoAck.superviseId());
        assertEquals(1, urgeTodoAck.result());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT ack_result FROM cm_warn_msg_urge_todo_req WHERE platform_id = ? AND supervise_id = ?",
                Integer.class, PLATFORM_ID, SUPERVISE_ID));
    }

    private boolean awaitLogin(long deadline) throws InterruptedException {
        if (System.currentTimeMillis() > deadline) {
            return false;
        }
        Jt809Frame login = awaitFrame(Jt809Constants.MSG_MAIN_LINK_LOGIN, frame -> true,
                Math.max(deadline - System.currentTimeMillis(), 1_000L));
        MainLinkLogin body = MainLinkLogin.parse(login.body());
        assertEquals(PLATFORM_ID, body.userId());
        assertEquals(SUB_LINK_PORT, body.subLinkPort());
        SUPERIOR.replyMain(Jt809Constants.MSG_MAIN_LINK_LOGIN_ACK,
                new MainLinkLoginAck(MainLinkLoginAck.RESULT_SUCCESS, PLATFORM_ID).encode());
        return true;
    }

    private void awaitUsableLink(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (linkManager.session().hasUsableLink()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("809 链路未进入可用状态");
    }

    private void awaitLinkDown(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!linkManager.session().hasUsableLink()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("809 链路未在超时内断开");
    }

    private long pendingOutbox() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cm_gov809_outbox WHERE platform_id = ? AND outbox_status = 'PENDING'",
                Long.class, PLATFORM_ID);
        return count == null ? 0L : count;
    }

    private void awaitPending(long expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (pendingOutbox() == expected) {
                return;
            }
            Thread.sleep(100L);
        }
        assertEquals(expected, pendingOutbox());
    }

    private void awaitUrgeTodoRow() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cm_warn_msg_urge_todo_req WHERE platform_id = ? AND supervise_id = ?",
                    Integer.class, PLATFORM_ID, SUPERVISE_ID);
            if (count != null && count == 1) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("督办请求未落库");
    }

    private Jt809Frame awaitFrame(int messageId, Predicate<Jt809Frame> extra, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        List<Jt809Frame> skipped = new ArrayList<>();
        try {
            while (System.currentTimeMillis() < deadline) {
                Jt809Frame frame = SUPERIOR.frames.poll(100L, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                if (frame.messageId() == messageId && extra.test(frame)) {
                    return frame;
                }
                skipped.add(frame);
            }
        } finally {
            SUPERIOR.frames.addAll(skipped);
        }
        throw new AssertionError("未在超时内收到 0x" + Integer.toHexString(messageId));
    }

    private void prepareVehicle() {
        jdbcTemplate.update("DELETE FROM cm_vehicle WHERE sim_no = ? OR plate_no = ?", SIM_NO, PLATE_NO);
        jdbcTemplate.update("INSERT INTO cm_vehicle (id, company_id, department_id, terminal_id, plate_no,"
                        + " plate_color, sim_no, create_time, update_time) VALUES (?, 0, 0, 0, ?, ?, ?, NOW(3), NOW(3))",
                990809L, PLATE_NO, PLATE_COLOR, SIM_NO);
    }

    private PositionEvent event(String eventId, String deviceTime) {
        PositionSample sample = new PositionSample(2, 1, 30000000, 123456789, 35, 600, 180,
                12340L, 0, 620, 0, 0);
        return new PositionEvent(PositionEvent.SCHEMA_VERSION, eventId, PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, SIM_NO, 7, deviceTime, deviceTime, deviceTime,
                "NORMAL", "gw-1", eventId, 0, sample, "digest");
    }

    private UrgeTodoRequest urgeTodoRequest() {
        return new UrgeTodoRequest(PLATE_NO, PLATE_COLOR, 1, 2, 1763107200L, SUPERVISE_ID,
                1763114400L, 1, "张三", "13800000000", "a@b.com");
    }

    private static int subType(byte[] body) {
        return Jt809Bytes.unsignedShort(body, Jt809Constants.PLATE_LENGTH + 1);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("无法分配空闲端口", ex);
        }
    }

    /** 用阻塞 Socket 模拟上级平台：接主链路、回连从链路、双向收帧。 */
    static final class StubSuperior {

        private final int port;

        private final BlockingQueue<Jt809Frame> frames = new LinkedBlockingQueue<>();

        private volatile ServerSocket serverSocket;

        private volatile Socket mainSocket;

        private volatile OutputStream mainOutput;

        private volatile Socket subSocket;

        private volatile OutputStream subOutput;

        private volatile boolean running;

        StubSuperior(int port) {
            this.port = port;
        }

        void start() {
            running = true;
            openServer();
        }

        void restart() {
            running = true;
            openServer();
        }

        void stop() {
            running = false;
            closeAll();
        }

        void closeAll() {
            closeQuietly(serverSocket);
            serverSocket = null;
            closeQuietly(mainSocket);
            mainSocket = null;
            mainOutput = null;
            closeQuietly(subSocket);
            subSocket = null;
            subOutput = null;
            frames.clear();
        }

        void replyMain(int messageId, byte[] body) {
            write(mainOutput, messageId, body);
        }

        void connectSubLink() {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", subLinkPort()), 3_000);
                subSocket = socket;
                subOutput = socket.getOutputStream();
                readFrames(socket);
                write(subOutput, Jt809Constants.MSG_SUB_LINK_LOGIN, new SubLinkLogin(PLATFORM_ID).encode());
            } catch (IOException ex) {
                throw new IllegalStateException("模拟从链路失败", ex);
            }
        }

        void sendUrgeTodo(UrgeTodoRequest request) {
            write(mainOutput, Jt809Constants.MSG_WARN_URGE_TODO, request.encode());
        }

        private int subLinkPort() {
            MainLinkLogin login = lastLogin;
            assertNotNull(login, "尚未收到 0x1001，无法得到从链路端口");
            return login.subLinkPort();
        }

        private volatile MainLinkLogin lastLogin;

        private void openServer() {
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("127.0.0.1", port));
                serverSocket = socket;
            } catch (IOException ex) {
                throw new IllegalStateException("模拟上级监听失败, port=" + port, ex);
            }
            Thread acceptor = new Thread(this::acceptLoop, "stub-superior-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private void acceptLoop() {
            while (running) {
                ServerSocket server = serverSocket;
                if (server == null || server.isClosed()) {
                    return;
                }
                try {
                    Socket socket = server.accept();
                    mainSocket = socket;
                    mainOutput = socket.getOutputStream();
                    readFrames(socket);
                } catch (IOException ex) {
                    return;
                }
            }
        }

        private void readFrames(Socket socket) {
            Thread reader = new Thread(() -> {
                try (InputStream input = socket.getInputStream()) {
                    while (running && !socket.isClosed()) {
                        Jt809Frame frame = readFrame(input);
                        if (frame.messageId() == Jt809Constants.MSG_MAIN_LINK_LOGIN) {
                            lastLogin = MainLinkLogin.parse(frame.body());
                        }
                        frames.add(frame);
                    }
                } catch (IOException ex) {
                    // 连接关闭即退出读取线程。
                }
            }, "stub-superior-reader");
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
            if (output == null) {
                throw new IllegalStateException("模拟上级尚无可用连接");
            }
            try {
                output.write(Jt809FrameCodec.encode(messageId, 1L, PLATFORM_ID, body));
                output.flush();
            } catch (IOException ex) {
                throw new IllegalStateException("模拟上级写入失败", ex);
            }
        }

        private static void closeQuietly(AutoCloseable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception ex) {
                // 关闭异常不影响测试结论。
            }
        }
    }
}
 
