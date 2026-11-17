package com.cm.admin;

import com.cm.admin.service.DepartmentAppService;
import com.cm.admin.service.UserAppService;
import com.cm.admin.service.VehicleAppService;
import com.cm.admin.vo.UserDetailVO;
import com.cm.infrastructure.service.MenuQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 上的数据范围与权限码验证（车辆与用户两个域）。
 * 默认不跑，设置 CM_P4_DB_TEST=true 且已执行 deploy/p4/seed-auth.sql 后启用。
 * HTTP 链路（CSRF、会话 cookie、401/403 状态码）由 P4HttpFlowTest 覆盖，本测试只管 SQL 与范围计算本身。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${CM_P4_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P4_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P4_DB_PASSWORD:CmApp@2026}",
        "cm.command.relay.enabled=false",
        "cm.telemetry.mq.consumers-enabled=false"
})
class P4AuthScopeDbTest {

    private static final long SUPER_ADMIN_ID = 100000000000000001L;

    private static final long OPERATOR_ID = 100000000000120201L;

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final int PAGE_SIZE = 20;

    @Autowired
    private VehicleAppService vehicleAppService;

    @Autowired
    private UserAppService userAppService;

    @Autowired
    private DepartmentAppService departmentAppService;

    @Autowired
    private MenuQueryService menuQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_return_all_vehicles_for_super_admin() {
        long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_vehicle v"
                        + " JOIN car_monitoring.cm_department d ON d.id = v.department_id"
                        + " WHERE v.is_deleted = 0 AND d.is_deleted = 0",
                Long.class);

        assertEquals(expected, vehicleAppService.page(SUPER_ADMIN_ID, 1, PAGE_SIZE, null, null, null, null)
                .getTotal());
    }

    @Test
    void should_limit_vehicles_to_own_department_for_operator() {
        long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_vehicle v"
                        + " JOIN car_monitoring.cm_department d ON d.id = v.department_id"
                        + " WHERE v.is_deleted = 0 AND d.is_deleted = 0 AND v.department_id = ?",
                Long.class, DALIAN_DEPARTMENT_ID);

        assertEquals(expected, vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, null, null)
                .getTotal());
    }

    @Test
    void should_allow_department_filter_inside_scope() {
        var page = vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, DALIAN_DEPARTMENT_ID, null);

        assertTrue(page.getTotal() > 0, "op1 绑定的部门应该有车");
        assertTrue(page.getRecords().stream().allMatch(row -> DALIAN_DEPARTMENT_ID == row.getDepartmentId()));
    }

    @Test
    void should_reject_department_filter_outside_scope() {
        assertThrows(AccessDeniedException.class,
                () -> vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, HUNAN_DEPARTMENT_ID, null));
    }

    @Test
    void should_keep_scope_when_keyword_does_not_match_anything() {
        assertEquals(0, vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, "不存在的车牌", null, null, null)
                .getTotal());
    }

    @Test
    void should_return_ten_menus_from_p4_source_only() {
        assertEquals(10, menuQueryService.menuTree().size());
        assertEquals("organization:view", menuQueryService.menuTree().get(0).getPermissionCode());
    }

    @Test
    void should_grant_menu_and_button_codes_to_operator() {
        // P4-2 起 op1 还要能进用户列表（organization:view）与点开详情（organization:user:detail），
        // P4-3/P4-4 起再授实时监控（monitor:view）与终端指令页（command:view / command:send），
        // 权限仍只覆盖本职页面，车辆分组增删改等写权限不在其中。
        assertEquals(List.of("command:send", "command:view", "monitor:view", "organization:user:detail",
                        "organization:view", "vehicle:view"),
                menuQueryService.permissionCodes(OPERATOR_ID, false));
    }

    @Test
    void should_return_single_department_for_operator() {
        assertEquals(1, departmentAppService.list(OPERATOR_ID).size());
        assertEquals(DALIAN_DEPARTMENT_ID, departmentAppService.list(OPERATOR_ID).get(0).getId());
    }

    @Test
    void should_return_all_users_for_super_admin() {
        long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_user u WHERE u.is_deleted = 0", Long.class);

        assertEquals(expected, userAppService.page(SUPER_ADMIN_ID, 1, PAGE_SIZE, null, null).getTotal());
    }

    /**
     * 用户列表的范围口径：被查看用户的可访问部门与当前账号 departmentIds 有交集。
     * 期望值直接按同一条口径在库里数，不写死数字：sa 绑了 6 个部门（含大连中心），admin 没有任何部门绑定。
     */
    @Test
    void should_limit_user_list_to_shared_departments_for_operator() {
        long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_user u"
                        + " WHERE u.is_deleted = 0"
                        + " AND EXISTS (SELECT 1 FROM car_monitoring.cm_user_department ud"
                        + " WHERE ud.user_id = u.id AND ud.is_deleted = 0 AND ud.department_id = ?)",
                Long.class, DALIAN_DEPARTMENT_ID);

        assertEquals(expected, userAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null).getTotal());
        assertTrue(expected < userAppService.page(SUPER_ADMIN_ID, 1, PAGE_SIZE, null, null).getTotal(),
                "受限账号看得到的用户数必须少于超管");
    }

    @Test
    void should_allow_user_department_filter_inside_scope() {
        long all = userAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null).getTotal();
        long filtered = userAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, DALIAN_DEPARTMENT_ID).getTotal();

        assertEquals(all, filtered, "op1 只有大连中心，按它筛选不应改变结果集");
    }

    @Test
    void should_reject_user_department_filter_outside_scope() {
        assertThrows(AccessDeniedException.class,
                () -> userAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, HUNAN_DEPARTMENT_ID));
    }

    @Test
    void should_reject_user_detail_outside_scope() {
        // admin 未绑定任何部门，与 op1 没有交集，必须拒绝。
        // id 按登录名从库里取：写死雪花 id 时，数据重装后断言会变成「查一个不存在的 id 也返 403」而空转。
        long unboundAdminId = userIdByUsername("admin");
        assertEquals(0, countUserDepartments(unboundAdminId), "前提：admin 不应有任何部门绑定");

        assertThrows(AccessDeniedException.class,
                () -> userAppService.detail(OPERATOR_ID, unboundAdminId));
    }

    @Test
    void should_return_user_detail_with_roles_and_departments() {
        UserDetailVO detail = userAppService.detail(OPERATOR_ID, OPERATOR_ID);

        assertEquals("op1", detail.getUsername());
        assertTrue(detail.isEnabled());
        assertEquals(1, detail.getRoles().size());
        assertEquals("ROLE_MONITOR", detail.getRoles().get(0).getRoleCode());
        assertEquals(1, detail.getDepartments().size());
        assertEquals(DALIAN_DEPARTMENT_ID, detail.getDepartments().get(0).getId());
    }

    @Test
    void should_allow_super_admin_to_see_user_without_department() {
        UserDetailVO detail = userAppService.detail(SUPER_ADMIN_ID, userIdByUsername("admin"));

        assertEquals("admin", detail.getUsername());
        assertTrue(detail.getRoles().isEmpty(), "admin 没有角色绑定");
        assertTrue(detail.getDepartments().isEmpty(), "admin 没有部门绑定");
    }

    /**
     * 详情里的可访问部门同样受查看者范围约束：跨部门用户不能让查看者看到范围外的部门名。
     */
    @Test
    void should_limit_detail_departments_to_viewer_scope() {
        UserDetailVO detail = userAppService.detail(OPERATOR_ID, SUPER_ADMIN_ID);

        assertTrue(detail.getDepartments().size() > 0, "sa 与 op1 在大连中心有交集，应至少返回 1 个部门");
        assertTrue(detail.getDepartments().stream()
                        .allMatch(item -> DALIAN_DEPARTMENT_ID == item.getId()),
                "op1 只能看到自己范围内的部门");
    }

    private long userIdByUsername(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM car_monitoring.cm_user WHERE username = ? AND is_deleted = 0",
                Long.class, username);
    }

    private long countUserDepartments(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_monitoring.cm_user_department WHERE user_id = ? AND is_deleted = 0",
                Long.class, userId);
    }
}
