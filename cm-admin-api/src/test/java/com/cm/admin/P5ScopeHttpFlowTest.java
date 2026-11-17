package com.cm.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P5 权限穿透矩阵：未登录 401、受限账号无权限码一律 403、有权限码的跨范围车辆 403，
 * 同一批端点对超管可达（证明 403 由授权与数据范围产生，不是端点缺失）。
 * 默认不跑；设置 CM_P4_DB_TEST=true 且执行过 deploy/p4/seed-auth.sql 后启用。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${CM_P4_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P4_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P4_DB_PASSWORD:CmApp@2026}",
        "cm.command.relay.enabled=false",
        "cm.telemetry.mq.consumers-enabled=false"
})
class P5ScopeHttpFlowTest {

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"123321\"}";

    /** URL 编码后的查询串，时间窗取 2026-01-01，不依赖库内是否有行。 */
    private static final String TIME_WINDOW =
            "startTime=2026-01-01%2000:00:00&endTime=2026-01-01%2023:59:59";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("HTTP：未登录访问受保护端点一律 401")
    void should_reject_anonymous_access_to_protected_apis() throws Exception {
        HttpClient client = newClient();

        assertEquals(401, get(client, "/api/v1/reports/alarms?" + TIME_WINDOW).statusCode());
        assertEquals(401, get(client, "/api/v1/tracks?vehicleId=1&" + TIME_WINDOW).statusCode());
        assertEquals(401, get(client, "/api/v1/fences").statusCode());
        assertEquals(401, get(client, "/api/v1/alarms?" + TIME_WINDOW).statusCode());
        assertEquals(401, get(client, "/api/v1/commands").statusCode());
    }

    @Test
    @DisplayName("HTTP：受限账号访问无权限码的读接口与写接口一律 403")
    void should_reject_operator_without_permission_code() throws Exception {
        HttpClient operator = loginAs("op1");
        long hunanVehicleId = vehicleIdInDepartment(HUNAN_DEPARTMENT_ID);
        long alarmId = anyAlarmId();

        assertEquals(403, get(operator, "/api/v1/reports/alarms?" + TIME_WINDOW).statusCode());
        assertEquals(403, get(operator, "/api/v1/reports/alarms/export?" + TIME_WINDOW).statusCode());
        assertEquals(403, get(operator, "/api/v1/tracks?vehicleId=" + hunanVehicleId + "&" + TIME_WINDOW)
                .statusCode());
        assertEquals(403, get(operator, "/api/v1/fences").statusCode());
        assertEquals(403, get(operator, "/api/v1/alarms?" + TIME_WINDOW).statusCode());
        assertEquals(403, post(operator, "/api/v1/messages/broadcast", "{\"text\":\"越权广播\"}").statusCode());
        assertEquals(403, post(operator, "/api/v1/gov809/link-commands", "{\"linkCmd\":4097}").statusCode());
        assertEquals(403, post(operator, "/api/v1/alarms/" + alarmId + "/process", "{\"remark\":\"越权处理\"}")
                .statusCode());
    }

    @Test
    @DisplayName("HTTP：受限账号点名数据范围外车辆返回 403")
    void should_reject_cross_scope_position_query_for_operator() throws Exception {
        HttpClient operator = loginAs("op1");
        long hunanVehicleId = vehicleIdInDepartment(HUNAN_DEPARTMENT_ID);

        assertEquals(403, post(operator, "/api/v1/commands/position-query",
                "{\"vehicleId\":" + hunanVehicleId + "}").statusCode());
    }

    @Test
    @DisplayName("HTTP：同一批端点对超管可达，证明 403 来自授权而非端点缺失")
    void should_allow_super_admin_on_same_endpoints() throws Exception {
        HttpClient admin = loginAs("sa");

        assertEquals(200, get(admin, "/api/v1/reports/alarms?" + TIME_WINDOW).statusCode());
        assertEquals(200, get(admin, "/api/v1/tracks?vehicleId="
                + vehicleIdInDepartment(DALIAN_DEPARTMENT_ID) + "&" + TIME_WINDOW).statusCode());
        assertEquals(200, get(admin, "/api/v1/fences").statusCode());
        assertEquals(200, get(admin, "/api/v1/alarms?" + TIME_WINDOW).statusCode());
        assertEquals(200, get(admin, "/api/v1/commands").statusCode());
    }

    private HttpClient loginAs(String username) throws Exception {
        HttpClient client = newClient();
        csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, username))
                .statusCode());
        return client;
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
        String csrfToken = cookieValue(client, "XSRF-TOKEN");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
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

    private String cookieValue(HttpClient client, String name) {
        CookieManager manager = (CookieManager) client.cookieHandler().orElseThrow();
        return manager.getCookieStore().getCookies().stream()
                .filter(cookie -> name.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(name + " cookie 未写入"));
    }

    private long vehicleIdInDepartment(long departmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM car_monitoring.cm_vehicle"
                        + " WHERE department_id = ? AND is_deleted = 0 ORDER BY id LIMIT 1",
                Long.class, departmentId);
    }

    /** 报警处理越权用例只需要一个真实存在的报警 ID；库里没有报警时退回 1（仍会先命中 403）。 */
    private long anyAlarmId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM car_monitoring.cm_alarm_record WHERE is_deleted = 0 ORDER BY id LIMIT 1",
                Long.class);
        return ids.isEmpty() ? 1L : ids.get(0);
    }
}
