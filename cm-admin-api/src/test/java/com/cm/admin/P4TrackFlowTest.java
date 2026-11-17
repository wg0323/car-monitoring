package com.cm.admin;

import com.alibaba.excel.EasyExcel;
import com.cm.admin.vo.TrackPointVO;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.timeseries.Granularity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轨迹切片端到端对账（TC-CMD-19 / TC-CMD-21 / TC-DAT-01）：
 * 页面 total、返回点数与导出 xlsx 数据行必须同为 5，首行车牌/时间/坐标/速度逐列相割。
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
        "cm.telemetry.mongo.auto-init=false",
        "cm.telemetry.mongo.history-collection=position_track_flow_test"
})
class P4TrackFlowTest {

    private static final String COLLECTION = "position_track_flow_test";

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"123321\"}";

    private static final DateTimeFormatter PARAM_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter TEXT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Instant BASE = Instant.parse("2026-11-15T04:00:00Z");

    private static final int POINT_COUNT = 5;

    private static final int SPEED_RAW = 60;

    private static final int LATITUDE_RAW = 30000000;

    private static final int LONGITUDE_RAW = 120000000;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    private VehicleRef vehicle;

    @BeforeEach
    void setUp() {
        vehicle = jdbcTemplate.queryForObject(
                "SELECT id, plate_no, sim_no FROM car_monitoring.cm_vehicle"
                        + " WHERE is_deleted = 0 AND sim_no IS NOT NULL AND sim_no != '' LIMIT 1",
                (rs, rowNum) -> new VehicleRef(rs.getLong("id"), rs.getString("plate_no"), rs.getString("sim_no")));
        mongoTemplate.dropCollection(COLLECTION);
        mongoTemplate.createCollection(COLLECTION, CollectionOptions.timeSeries("deviceTime",
                series -> series.metaField("meta").granularity(Granularity.SECONDS)));
        for (int index = 0; index < POINT_COUNT; index++) {
            mongoTemplate.insert(trackDocument(index), COLLECTION);
        }
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.dropCollection(COLLECTION);
    }

    @Test
    @DisplayName("HTTP：页面 total、点数与导出行数三方一致，首行字段逐列相符")
    void should_keep_page_and_export_consistent() throws Exception {
        HttpClient client = loggedIn("sa");
        String path = queryPath();

        HttpResponse<String> page = get(client, path);
        assertEquals(200, page.statusCode(), page.body());
        assertTrue(page.body().contains("\"total\":" + POINT_COUNT), page.body());
        assertTrue(page.body().contains("\"latitude\":30.000000"), page.body());
        assertTrue(page.body().contains("\"longitude\":120.000000"), page.body());
        assertTrue(page.body().contains("\"speedKph\":6.0"), page.body());
        assertTrue(page.body().contains("\"deviceTimeText\":\""
                + TEXT_FORMATTER.format(LocalDateTime.ofInstant(BASE, ZONE)) + "\""), page.body());

        HttpResponse<byte[]> export = getBytes(client, exportPath());
        assertEquals(200, export.statusCode());
        List<TrackPointVO> rows = EasyExcel.read(new ByteArrayInputStream(export.body()))
                .head(TrackPointVO.class).sheet().doReadSync();

        assertEquals(POINT_COUNT, rows.size(), "导出数据行必须与页面 total 一致");
        TrackPointVO first = rows.get(0);
        assertEquals(vehicle.plateNo(), first.getPlateNo());
        assertEquals(TEXT_FORMATTER.format(LocalDateTime.ofInstant(BASE, ZONE)), first.getDeviceTimeText());
        assertEquals(0, first.getLatitude().compareTo(new BigDecimal("30.000000")));
        assertEquals(0, first.getLongitude().compareTo(new BigDecimal("120.000000")));
        assertEquals(0, first.getSpeedKph().compareTo(new BigDecimal("6.0")));
    }

    @Test
    @DisplayName("HTTP：未授予 track:view 的账号返回 403")
    void should_forbid_user_without_track_permission() throws Exception {
        HttpClient client = loggedIn("op1");

        assertEquals(403, get(client, queryPath()).statusCode());
    }

    private String queryPath() {
        String start = PARAM_FORMATTER.format(LocalDateTime.ofInstant(BASE, ZONE));
        String end = PARAM_FORMATTER.format(LocalDateTime.ofInstant(BASE.plusSeconds(600), ZONE));
        return "/api/v1/tracks?vehicleId=" + vehicle.id() + "&startTime=" + start.replace(" ", "%20")
                + "&endTime=" + end.replace(" ", "%20");
    }

    private String exportPath() {
        return queryPath().replace("/api/v1/tracks?", "/api/v1/tracks/export?");
    }

    private Document trackDocument(int index) {
        Instant deviceTime = BASE.plusSeconds(index * 60L);
        Document meta = new Document("simNo", vehicle.simNo())
                .append("vehicleId", vehicle.id())
                .append("companyId", 1L);
        return new Document("_id", "FLOW-T" + index)
                .append("deviceTime", Date.from(deviceTime))
                .append("meta", meta)
                .append("eventId", "FLOW-T" + index)
                .append("simNo", vehicle.simNo())
                .append("latitude", LATITUDE_RAW)
                .append("longitude", LONGITUDE_RAW)
                .append("altitude", 15)
                .append("speed", SPEED_RAW)
                .append("recorderSpeed", 58)
                .append("course", 90)
                .append("mileage", 1234L)
                .append("oil", 56)
                .append("status", 3)
                .append("alarmFlag", 0);
    }

    private HttpClient loggedIn(String username) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String token = csrfToken(client);
        assertEquals(200, post(client, "/api/v1/auth/login", String.format(LOGIN_BODY, username), token)
                .statusCode());
        return client;
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
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
        CookieManager manager = (CookieManager) client.cookieHandler().orElseThrow();
        return manager.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("XSRF-TOKEN cookie 未写入"));
    }

    private record VehicleRef(long id, String plateNo, String simNo) {
    }
}

