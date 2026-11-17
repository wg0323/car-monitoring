package com.cm.admin;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 HTTP 链路：未登录 401 → 登录 → 会话 cookie → 菜单与权限码 → 车辆与用户 total → 跨范围 403。
 * 默认不跑；设置 CM_P4_DB_TEST=true、V9 迁移已执行且已导入 deploy/p4/seed-auth.sql 后启用。
 * 与 P4AuthScopeDbTest 的分工：那个测 SQL 与范围计算，本测试测 CSRF、会话 cookie 与状态码。
 * 类名以 Test 结尾，才能被 surefire 默认收进 mvn test，而不是只靠手工 -Dtest 触发。
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
class P4HttpFlowTest {

    private static final Pattern TOTAL_PATTERN = Pattern.compile("\"total\":(\\d+)");

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final long OPERATOR_ID = 100000000000120201L;

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"123321\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_reject_anonymous_vehicle_query() throws Exception {
        HttpClient client = newClient();

        assertEquals(401, get(client, "/api/v1/vehicles").statusCode());
    }

    @Test
    void should_limit_operator_to_own_department() throws Exception {
        HttpClient client = newClient();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        String me = get(client, "/api/v1/auth/me").body();
        assertTrue(me.contains("\"permissionCode\":\"vehicle:view\""), me);
        assertFalse(me.contains("system:view"), me);

        String own = get(client, "/api/v1/vehicles?page=1&size=5").body();
        assertEquals(expectedTotal(DALIAN_DEPARTMENT_ID), total(own));

        String departments = get(client, "/api/v1/departments").body();
        assertEquals(1, countOccurrences(departments, "\"parentId\""));
    }

    @Test
    void should_reject_cross_department_filter() throws Exception {
        HttpClient client = newClient();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        assertEquals(403, get(client, "/api/v1/vehicles?departmentId=" + HUNAN_DEPARTMENT_ID).statusCode());

        String sneaky = get(client, "/api/v1/vehicles?superAdmin=true&depIdList=" + HUNAN_DEPARTMENT_ID).body();
        assertTrue(total(sneaky) > 0);
        assertEquals(expectedTotal(DALIAN_DEPARTMENT_ID), total(sneaky));
    }

    @Test
    void should_return_full_scope_for_super_admin() throws Exception {
        HttpClient client = newClient();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, "sa"), token).statusCode());

        String me = get(client, "/api/v1/auth/me").body();
        assertEquals(10, countOccurrences(me, "\"permissionCode\""));

        String all = get(client, "/api/v1/vehicles?page=1&size=20").body();
        assertEquals(expectedTotal(null), total(all));
    }

    @Test
    void should_limit_user_list_and_detail_by_scope() throws Exception {
        HttpClient operator = newClient();
        String token = csrfToken(operator);
        assertEquals(200, post(operator, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        String list = get(operator, "/api/v1/users?page=1&size=20").body();
        assertEquals(expectedUserTotal(DALIAN_DEPARTMENT_ID), total(list));

        // 部门筛选值越界必须是 403，不能退化成 200 + 空列表。
        assertEquals(403, get(operator, "/api/v1/users?departmentId=" + HUNAN_DEPARTMENT_ID).statusCode());

        // admin 未绑部门（先断言这个前提，否则「查不存在的 id 也返 403」会让断言空转），与 op1 无交集，详情必须 403。
        long unboundAdminId = userIdByUsername("admin");
        assertEquals(0, countUserDepartments(unboundAdminId), "前提：admin 不应有任何部门绑定");
        assertEquals(403, get(operator, "/api/v1/users/" + unboundAdminId).statusCode());

        String detail = get(operator, "/api/v1/users/" + OPERATOR_ID).body();
        assertTrue(detail.contains("ROLE_MONITOR"), detail);

        HttpClient admin = newClient();
        String adminToken = csrfToken(admin);
        assertEquals(200, post(admin, "/api/v1/auth/login", String.format(LOGIN_BODY, "sa"), adminToken).statusCode());

        assertEquals(expectedUserTotal(null), total(get(admin, "/api/v1/users?page=1&size=20").body()));
    }

    @Test
    void should_reject_vehicle_detail_outside_scope_over_http() throws Exception {
        long hunanVehicleId = vehicleIdInDepartment(HUNAN_DEPARTMENT_ID);
        HttpClient operator = newClient();
        String token = csrfToken(operator);
        assertEquals(200, post(operator, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        // 越界与不存在都必须是 403，不能退化成 200 + 空对象。
        assertEquals(403, get(operator, "/api/v1/vehicles/" + hunanVehicleId).statusCode());

        long dalianVehicleId = vehicleIdInDepartment(DALIAN_DEPARTMENT_ID);
        String detail = get(operator, "/api/v1/vehicles/" + dalianVehicleId).body();
        assertTrue(detail.contains("\"plateColorName\""), detail);
        assertTrue(detail.contains("\"departmentName\""), detail);
    }

    @Test
    void should_reject_department_write_for_restricted_account() throws Exception {
        HttpClient operator = newClient();
        String token = csrfToken(operator);
        assertEquals(200, post(operator, "/api/v1/auth/login", String.format(LOGIN_BODY, "op1"), token).statusCode());

        // 车辆分组增删改未授权给 op1：服务端必须是 403，而不是写成功。
        assertEquals(403, post(operator, "/api/v1/departments",
                "{\"name\":\"越权车组\",\"parentId\":0}", token).statusCode());
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

    private int countOccurrences(String body, String token) {
        int count = 0;
        int index = body.indexOf(token);
        while (index >= 0) {
            count++;
            index = body.indexOf(token, index + token.length());
        }
        return count;
    }

    private int expectedTotal(Long departmentId) {
        if (departmentId == null) {
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM car_monitoring.cm_vehicle v"
                            + " JOIN car_monitoring.cm_department d ON d.id = v.department_id"
                            + " WHERE v.is_deleted = 0 AND d.is_deleted = 0",
                    Long.class);
            return total.intValue();
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_vehicle v"
                        + " JOIN car_monitoring.cm_department d ON d.id = v.department_id"
                        + " WHERE v.is_deleted = 0 AND d.is_deleted = 0 AND v.department_id = ?",
                Long.class, departmentId);
        return total.intValue();
    }

    /**
     * 用户列表期望值：与被查部门有未删关联的用户数；departmentId 为 null 时取全量，与超管口径一致。
     */
    private int expectedUserTotal(Long departmentId) {
        String sql = "SELECT COUNT(*) FROM car_monitoring.cm_user u WHERE u.is_deleted = 0";
        Long total;
        if (departmentId == null) {
            total = jdbcTemplate.queryForObject(sql, Long.class);
        } else {
            total = jdbcTemplate.queryForObject(sql
                    + " AND EXISTS (SELECT 1 FROM car_monitoring.cm_user_department ud"
                    + " WHERE ud.user_id = u.id AND ud.is_deleted = 0 AND ud.department_id = ?)",
                    Long.class, departmentId);
        }
        return total.intValue();
    }

    private long userIdByUsername(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM car_monitoring.cm_user WHERE username = ? AND is_deleted = 0",
                Long.class, username);
    }

    private long vehicleIdInDepartment(long departmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM car_monitoring.cm_vehicle"
                        + " WHERE department_id = ? AND is_deleted = 0 ORDER BY id LIMIT 1",
                Long.class, departmentId);
    }

    private long countUserDepartments(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_user_department WHERE user_id = ? AND is_deleted = 0",
                Long.class, userId);
    }
}
