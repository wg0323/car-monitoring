package com.cm.admin;

import com.cm.infrastructure.telemetry.PositionBroadcast;
import com.cm.infrastructure.telemetry.PositionEvent;
import com.cm.infrastructure.telemetry.PositionSample;
import com.cm.infrastructure.telemetry.TelemetryJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实时监控真库链路：HTTP 范围过滤 + WebSocket 握手、订阅校验、位置扇出与会话失效断开。
 * 默认不跑；设置 CM_P4_DB_TEST=true、V9 迁移已执行且已导入 deploy/p4/seed-auth.sql 后启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${CM_P4_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P4_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P4_DB_PASSWORD:CmApp@2026}",
        "cm.command.relay.enabled=false",
        "cm.telemetry.mq.consumers-enabled=false",
        "cm.monitor.summary-cache-seconds=0",
        "cm.monitor.session.check-interval-millis=300",
        "cm.monitor.session.heartbeat-timeout-millis=60000",
        "cm.monitor.session.account-check-interval-millis=600000",
        "cm.monitor.subscribe.max-vehicles-per-session=10"
})
class P4MonitorFlowTest {

    private static final Pattern TOTAL_PATTERN = Pattern.compile("\"total\":(\\d+)");

    private static final Pattern ONLINE_PATTERN = Pattern.compile("\"online\":(\\d+)");

    private static final Pattern PARKING_PATTERN = Pattern.compile("\"parking\":(\\d+)");

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"123321\"}";

    private static final String POSITION_CHANNEL = "cm:telemetry:position";

    private static final String ONLINE_KEY_PREFIX = "cm:online:";

    private static final String POSITION_KEY_PREFIX = "cm:position:";

    private static final String SESSION_COOKIE_NAME = "SESSION";

    private static final long WAIT_SECONDS = 5L;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<String> touchedKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        touchedKeys.forEach(redisTemplate::delete);
        touchedKeys.clear();
    }

    @Test
    @DisplayName("HTTP：实时列表 total 与库内大连中心车辆数一致，越界部门返回 403")
    void should_limit_monitor_list_by_scope() throws Exception {
        HttpClient client = loggedInOperator();

        assertEquals(expectedVehicleTotal(DALIAN_DEPARTMENT_ID),
                total(get(client, "/api/v1/monitor/vehicles?page=1&size=20").body()));
        assertEquals(403, get(client, "/api/v1/monitor/vehicles?departmentId=" + HUNAN_DEPARTMENT_ID).statusCode());
        assertEquals(200, get(client, "/api/v1/monitor/vehicles?page=1&size=20"
                + "&departmentId=" + DALIAN_DEPARTMENT_ID).statusCode());
    }

    @Test
    @DisplayName("HTTP：状态栏按租约与最新位置统计在线与停车")
    void should_summarize_online_and_parking() throws Exception {
        HttpClient client = loggedInOperator();
        List<VehicleRef> vehicles = vehiclesInScope(2);
        String baseline = get(client, "/api/v1/monitor/summary").body();
        int baselineOnline = match(ONLINE_PATTERN, baseline);
        int baselineParking = match(PARKING_PATTERN, baseline);

        for (VehicleRef vehicle : vehicles) {
            setOnline(vehicle.simNo());
            setLatestPosition(vehicle.simNo(), 0);
        }

        String summary = get(client, "/api/v1/monitor/summary").body();

        assertEquals(baselineOnline + vehicles.size(), match(ONLINE_PATTERN, summary), summary);
        assertEquals(baselineParking + vehicles.size(), match(PARKING_PATTERN, summary), summary);
        assertTrue(summary.contains("\"total\":" + expectedVehicleTotal(DALIAN_DEPARTMENT_ID)), summary);
    }

    @Test
    @DisplayName("WS：未登录握手被拒绝")
    void should_reject_anonymous_handshake() {
        HttpClient anonymous = HttpClient.newHttpClient();
        WebSocket.Builder builder = anonymous.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5));

        assertThrows(CompletionException.class, () -> builder
                .buildAsync(URI.create(wsUrl()), new CollectingListener()).join());
    }

    @Test
    @DisplayName("WS：订阅范围内车辆后能收到位置帧")
    void should_push_position_for_subscribed_vehicle() throws Exception {
        HttpClient client = loggedInOperator();
        VehicleRef vehicle = vehiclesInScope(1).get(0);
        CollectingListener listener = new CollectingListener();
        WebSocket socket = openSocket(client, listener);
        try {
            socket.sendText("{\"type\":\"SUBSCRIBE\",\"vehicleIds\":[\"" + vehicle.id() + "\"]}", true).join();
            assertPong(socket, listener);

            publishPosition(vehicle.simNo(), 603);

            String frame = listener.poll();
            assertNotNull(frame, "未收到位置帧");
            assertTrue(frame.contains("\"type\":\"POSITION\""), frame);
            assertTrue(frame.contains("\"vehicleId\":\"" + vehicle.id() + "\""), frame);
            assertTrue(frame.contains("\"speedKph\":60.3"), frame);
        } finally {
            socket.abort();
        }
    }

    @Test
    @DisplayName("WS：订阅范围外车辆只回越界错误，不推任何数据")
    void should_reject_out_of_scope_subscription() throws Exception {
        HttpClient client = loggedInOperator();
        VehicleRef outside = vehiclesInDepartment(HUNAN_DEPARTMENT_ID);
        CollectingListener listener = new CollectingListener();
        WebSocket socket = openSocket(client, listener);
        try {
            socket.sendText("{\"type\":\"SUBSCRIBE\",\"vehicleIds\":[\"" + outside.id() + "\"]}", true).join();

            String frame = listener.poll();
            assertNotNull(frame, "应收到越界错误帧");
            assertTrue(frame.contains("\"code\":4403"), frame);

            publishPosition(outside.simNo(), 0);
            assertNullFrame(listener);
        } finally {
            socket.abort();
        }
    }

    @Test
    @DisplayName("WS：会话仍在时不踢连接，登出后才关闭")
    void should_close_connection_when_session_gone() throws Exception {
        HttpClient client = loggedInOperator();
        CollectingListener listener = new CollectingListener();
        WebSocket socket = openSocket(client, listener);
        try {
            assertPong(socket, listener);
            // 先跨过两轮会话复核：连接里存错会话 ID 时这里就会拿到 4401，用例不至于空转通过。
            Thread.sleep(800L);
            assertPong(socket, listener);

            // 登出会真正删掉服务端会话；直接删 Redis 键不行，Cookie 里的值是 Base64 后的，与仓库里的会话 ID 不同。
            String token = csrfToken(client);
            assertEquals(200, post(client, "/api/v1/auth/logout", "", token).statusCode());

            assertTrue(listener.awaitClose(), "会话失效后连接应被服务端关闭");
            assertEquals(4401, listener.closeCode());
        } finally {
            socket.abort();
        }
    }

    private void assertNullFrame(CollectingListener listener) throws InterruptedException {
        String frame = listener.poll(1);
        assertFalse(frame != null && frame.contains("\"type\":\"POSITION\""), "越界车辆不应收到位置帧: " + frame);
    }

    private void assertPong(WebSocket socket, CollectingListener listener) throws Exception {
        // 订阅与心跳走同一条连接、按序处理：收到 PONG 即证明前面的 SUBSCRIBE 已经处理完。
        socket.sendText("{\"type\":\"PING\"}", true).join();
        String frame = listener.poll();
        assertNotNull(frame, "未收到心跳应答");
        assertTrue(frame.contains("\"type\":\"PONG\""), frame);
    }

    private WebSocket openSocket(HttpClient client, CollectingListener listener) throws Exception {
        WebSocket.Builder builder = client.newWebSocketBuilder()
                .header("Cookie", sessionCookie(client))
                .connectTimeout(Duration.ofSeconds(5));
        return builder.buildAsync(URI.create(wsUrl()), listener).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private String wsUrl() {
        return "ws://127.0.0.1:" + port + "/ws/monitor";
    }

    private HttpClient loggedInOperator() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());
        return client;
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String body, String csrfToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String csrfToken(HttpClient client) throws Exception {
        assertEquals(200, get(client, "/api/v1/auth/csrf").statusCode());
        return cookieValue(client, "XSRF-TOKEN");
    }

    private String sessionCookie(HttpClient client) throws Exception {
        return SESSION_COOKIE_NAME + "=" + cookieValue(client, SESSION_COOKIE_NAME);
    }

    private String cookieValue(HttpClient client, String name) {
        CookieManager manager = (CookieManager) client.cookieHandler().orElseThrow();
        return manager.getCookieStore().getCookies().stream()
                .filter(cookie -> name.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(name + " cookie 未写入"));
    }

    private int total(String body) {
        Matcher matcher = TOTAL_PATTERN.matcher(body);
        assertTrue(matcher.find(), body);
        return Integer.parseInt(matcher.group(1));
    }

    private int match(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), body);
        return Integer.parseInt(matcher.group(1));
    }

    private int expectedVehicleTotal(Long departmentId) {
        String sql = "SELECT COUNT(*) FROM car_monitoring.cm_vehicle v"
                + " JOIN car_monitoring.cm_department d ON d.id = v.department_id"
                + " WHERE v.is_deleted = 0 AND d.is_deleted = 0 AND v.department_id = ?";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, departmentId);
        return total == null ? 0 : total.intValue();
    }

    private List<VehicleRef> vehiclesInScope(int limit) {
        List<VehicleRef> vehicles = jdbcTemplate.query(
                "SELECT id, sim_no FROM car_monitoring.cm_vehicle"
                        + " WHERE is_deleted = 0 AND department_id = ? AND sim_no IS NOT NULL"
                        + " ORDER BY id LIMIT ?",
                (rs, rowNum) -> new VehicleRef(rs.getLong("id"), rs.getString("sim_no")),
                DALIAN_DEPARTMENT_ID, limit);
        assertEquals(limit, vehicles.size(), "大连中心应有足够车辆用于订阅校验");
        return vehicles;
    }

    private VehicleRef vehiclesInDepartment(long departmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, sim_no FROM car_monitoring.cm_vehicle"
                        + " WHERE is_deleted = 0 AND department_id = ? AND sim_no IS NOT NULL LIMIT 1",
                (rs, rowNum) -> new VehicleRef(rs.getLong("id"), rs.getString("sim_no")),
                departmentId);
    }

    private void setOnline(String simNo) {
        String key = ONLINE_KEY_PREFIX + simNo;
        touchedKeys.add(key);
        redisTemplate.opsForValue().set(key, "gw-test|1", Duration.ofMinutes(2));
    }

    /**
     * 写一条与网关同构的最新位置：监控端按 PositionEvent 解析，字段少一个就会当成坏数据丢单。
     */
    private void setLatestPosition(String simNo, int speed) {
        String key = POSITION_KEY_PREFIX + simNo;
        touchedKeys.add(key);
        PositionSample sample = new PositionSample(0, 1, 30000000, 120000000, 0, speed, 90, 100L, 0, 0, 0, 0);
        PositionEvent event = new PositionEvent(PositionEvent.SCHEMA_VERSION, "monitor-test", PositionEvent.EVENT_TYPE,
                PositionEvent.BRANCH_STANDARD, simNo, 1, "2026-11-15T04:00:00Z", "2026-11-15T04:00:01Z",
                "2026-11-15T04:00:00Z", "NORMAL", "gw-test", "trace-test", 0, sample, "digest");
        redisTemplate.opsForHash().put(key, "ver", String.format("%013d|%013d|%s", 1L, 2L, event.eventId()));
        redisTemplate.opsForHash().put(key, "data", TelemetryJson.write(event));
    }

    private void publishPosition(String simNo, int speed) {
        PositionBroadcast event = new PositionBroadcast(PositionBroadcast.SCHEMA_VERSION, simNo, 30000000,
                120000000, speed, 90, 0, 1, "2026-11-15T04:00:00Z");
        redisTemplate.convertAndSend(POSITION_CHANNEL, TelemetryJson.write(event));
    }

    private record VehicleRef(long id, String simNo) {
    }

    /**
     * 收帧监听：文本帧按 last 标记拼回完整消息，关闭事件单独记码供断言。
     */
    private static final class CollectingListener implements WebSocket.Listener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        private final StringBuilder partial = new StringBuilder();

        private final CompletableFuture<Integer> closed = new CompletableFuture<>();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.complete(-1);
        }

        String poll() throws InterruptedException {
            return messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        String poll(long seconds) throws InterruptedException {
            return messages.poll(seconds, TimeUnit.SECONDS);
        }

        boolean awaitClose() throws Exception {
            return closed.get(WAIT_SECONDS, TimeUnit.SECONDS) != null;
        }

        int closeCode() throws Exception {
            return closed.get(1, TimeUnit.SECONDS);
        }
    }
}
