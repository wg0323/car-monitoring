package com.cm.admin;

import org.junit.jupiter.api.AfterEach;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 区域与地图中心的真实 HTTP 链路：401 → 权限码 403 → sa 建/查/改/删 → 个人中心覆盖系统中心。
 * 默认不跑；设置 CM_P4_DB_TEST=true、V10 迁移已执行且已导入 deploy/p4/seed-auth.sql 后启用。
 * 测试区域统一以 P4FenceHttp 开头，@AfterEach 物理清理；个人中心改完即恢复。
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
class P4MapFenceHttpFlowTest {

    private static final Pattern TOTAL_PATTERN = Pattern.compile("\"total\":(\\d+)");

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\":\"(\\d+)\"");

    private static final Pattern RADIUS_PATTERN = Pattern.compile("\"radius\":(\\d+)");

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long OPERATOR_ID = 100000000000120201L;

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"123321\"}";

    private static final String CREATE_BODY = "{\"name\":\"%s\",\"departmentId\":%d,\"areaType\":\"circle\","
            + "\"points\":\"121.500000,31.200000\",\"radius\":500,"
            + "\"alarmEnterPlatform\":true,\"alarmExitDriver\":true,"
            + "\"byTime\":false,\"limitSpeed\":false,\"delaySeconds\":30,\"remark\":\"自动化测试\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM car_monitoring.cm_map_area WHERE name LIKE 'P4FenceHttp%'");
        jdbcTemplate.update("UPDATE car_monitoring.cm_user SET map_center_lat = 0, map_center_lng = 0,"
                + " map_level = 0 WHERE id = ?", OPERATOR_ID);
    }

    @Test
    void should_reject_anonymous_fence_query() throws Exception {
        assertEquals(401, get(newClient(), "/api/v1/fences").statusCode());
    }

    @Test
    void should_reject_operator_without_fence_permission() throws Exception {
        HttpClient client = newClient();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        assertEquals(403, get(client, "/api/v1/fences").statusCode());
        assertEquals(403, post(client, "/api/v1/fences", String.format(CREATE_BODY, "P4FenceHttp越权",
                DALIAN_DEPARTMENT_ID), token).statusCode());
    }

    @Test
    void should_create_update_and_delete_circle() throws Exception {
        HttpClient client = newClient();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "sa"), token).statusCode());

        HttpResponse<String> created = post(client, "/api/v1/fences",
                String.format(CREATE_BODY, "P4FenceHttp圆形", DALIAN_DEPARTMENT_ID), token);
        assertEquals(200, created.statusCode(), created.body());
        long areaId = idOf(created.body());
        assertEquals(500, radiusOf(created.body()));

        String list = get(client, "/api/v1/fences?page=1&size=20&name=P4FenceHttp").body();
        assertEquals(1, total(list), list);

        String detail = get(client, "/api/v1/fences/" + areaId).body();
        assertTrue(detail.contains("\"areaType\":\"circle\""), detail);
        assertTrue(detail.contains("\"departmentName\":\"大连中心\""), detail);
        assertTrue(detail.contains("\"alarmEnterPlatform\":true"), detail);

        String updateBody = String.format(CREATE_BODY, "P4FenceHttp圆形改", DALIAN_DEPARTMENT_ID)
                .replace("\"radius\":500", "\"radius\":600");
        HttpResponse<String> updated = put(client, "/api/v1/fences/" + areaId, updateBody, token);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals(600, radiusOf(updated.body()));
        assertTrue(updated.body().contains("P4FenceHttp圆形改"), updated.body());

        assertEquals(200, delete(client, "/api/v1/fences/" + areaId, token).statusCode());
        assertEquals(400, get(client, "/api/v1/fences/" + areaId).statusCode());
        assertEquals(0, total(get(client, "/api/v1/fences?page=1&size=20&name=P4FenceHttp").body()));
    }

    @Test
    void should_save_map_center_and_read_back() throws Exception {
        HttpClient client = newClient();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        String body = "{\"centerLat\":38.914004,\"centerLng\":121.614682,\"zoom\":15}";
        HttpResponse<String> saved = put(client, "/api/v1/map/preference", body, token);
        assertEquals(200, saved.statusCode(), saved.body());
        assertTrue(saved.body().contains("\"saved\":true"), saved.body());

        String config = get(client, "/api/v1/map/config").body();
        assertTrue(config.contains("\"centerLat\":38.914004"), config);
        assertTrue(config.contains("\"centerLng\":121.614682"), config);
        assertTrue(config.contains("\"centerZoom\":15"), config);

        assertEquals(400, put(client, "/api/v1/map/preference",
                "{\"centerLat\":0,\"centerLng\":0,\"zoom\":12}", token).statusCode());
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

    private HttpResponse<String> post(HttpClient client, String path, String body, String csrfToken) throws Exception {
        return send(client, path, body, csrfToken, "POST");
    }

    private HttpResponse<String> put(HttpClient client, String path, String body, String csrfToken) throws Exception {
        return send(client, path, body, csrfToken, "PUT");
    }

    private HttpResponse<String> delete(HttpClient client, String path, String csrfToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("X-XSRF-TOKEN", csrfToken)
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(HttpClient client, String path, String body, String csrfToken, String method)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken)
                .timeout(Duration.ofSeconds(15))
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String csrfToken(HttpClient client) throws Exception {
        assertEquals(200, get(client, "/api/v1/auth/csrf").statusCode());
        CookieManager manager = (CookieManager) client.cookieHandler().orElseThrow();
        return manager.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("XSRF-TOKEN cookie 未写入"));
    }

    private int total(String body) {
        Matcher matcher = TOTAL_PATTERN.matcher(body);
        assertTrue(matcher.find(), body);
        return Integer.parseInt(matcher.group(1));
    }

    private long idOf(String body) {
        Matcher matcher = ID_PATTERN.matcher(body);
        assertTrue(matcher.find(), body);
        return Long.parseLong(matcher.group(1));
    }

    private int radiusOf(String body) {
        Matcher matcher = RADIUS_PATTERN.matcher(body);
        assertTrue(matcher.find(), body);
        return Integer.parseInt(matcher.group(1));
    }
}
