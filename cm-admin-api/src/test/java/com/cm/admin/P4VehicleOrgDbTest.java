package com.cm.admin;

import com.cm.admin.service.DepartmentAppService;
import com.cm.admin.service.VehicleAppService;
import com.cm.admin.vo.DepartmentDetailVO;
import com.cm.admin.vo.DepartmentSaveRequest;
import com.cm.admin.vo.VehicleDetailVO;
import com.cm.domain.exception.CmConflictException;
import com.cm.infrastructure.telemetry.TelemetryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 车辆详情、在线筛选与车辆分组 CRUD 的真实 MySQL/Redis 验证（TC-VT-05/10、TC-ADM-05）。
 * 默认不跑；设置 CM_P4_DB_TEST=true、V11 迁移已执行且已导入 deploy/p4/seed-auth.sql 后启用。
 * 自动化测试自己造的数据在 finally 里物理删除，不留给下次重跑。
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
class P4VehicleOrgDbTest {

    private static final long SUPER_ADMIN_ID = 100000000000000001L;

    private static final long OPERATOR_ID = 100000000000120201L;

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final int PAGE_SIZE = 20;

    @Autowired
    private VehicleAppService vehicleAppService;

    @Autowired
    private DepartmentAppService departmentAppService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TelemetryProperties telemetryProperties;

    @Test
    void should_return_vehicle_detail_within_scope() {
        long vehicleId = vehicleIdInDepartment(DALIAN_DEPARTMENT_ID);

        VehicleDetailVO detail = vehicleAppService.detail(OPERATOR_ID, vehicleId);

        assertNotNull(detail.getPlateNo());
        assertNotNull(detail.getPlateColorName());
        assertEquals(departmentName(DALIAN_DEPARTMENT_ID), detail.getDepartmentName());
        assertTrue(detail.getSimNo() != null, "终端卡号应可读");
    }

    @Test
    void should_reject_vehicle_detail_outside_scope() {
        long hunanVehicleId = vehicleIdInDepartment(HUNAN_DEPARTMENT_ID);

        assertThrows(AccessDeniedException.class, () -> vehicleAppService.detail(OPERATOR_ID, hunanVehicleId));
    }

    /**
     * 在线/离线筛选以 Redis 在线租约为准：写入一条租约后，在线 total 至少包含这台车，
     * 且「在线 + 离线 = 全部」始终成立。结束后删掉租约键，不影响后续用例。
     */
    @Test
    void should_split_online_and_offline_filter() {
        String simNo = jdbcTemplate.queryForObject(
                "SELECT sim_no FROM car_monitoring.cm_vehicle"
                        + " WHERE department_id = ? AND sim_no <> '' AND is_deleted = 0 ORDER BY id LIMIT 1",
                String.class, DALIAN_DEPARTMENT_ID);
        String key = telemetryProperties.getOnline().getKeyPrefix() + simNo;
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(2));
        try {
            long all = vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, null, null).getTotal();
            long online = vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, null, true).getTotal();
            long offline = vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, null, false).getTotal();

            assertEquals(all, online + offline);
            assertTrue(online >= 1);
            assertTrue(vehicleAppService.page(OPERATOR_ID, 1, PAGE_SIZE, null, null, null, true)
                    .getRecords().stream().anyMatch(row -> simNo.equals(row.getSimNo())));
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Test
    void should_manage_department_closed_loop() {
        String name = "验收车组" + System.currentTimeMillis();
        DepartmentSaveRequest request = new DepartmentSaveRequest();
        request.setName(name);
        request.setParentId(0L);
        request.setDepartmentType("验收");
        long createdId = 0L;
        try {
            DepartmentDetailVO created = departmentAppService.create(SUPER_ADMIN_ID, request);
            createdId = created.getId();
            assertTrue(managedIds().contains(createdId), "新增后列表应可查");

            request.setName(name + "改");
            DepartmentDetailVO updated = departmentAppService.update(SUPER_ADMIN_ID, createdId, request);
            assertEquals(name + "改", updated.getName(), "改名后应回读新名称");

            departmentAppService.delete(SUPER_ADMIN_ID, createdId);
            assertFalse(managedIds().contains(createdId), "删除后列表不再显示");
        } finally {
            if (createdId > 0) {
                jdbcTemplate.update("DELETE FROM car_monitoring.cm_user_department WHERE department_id = ?",
                        createdId);
                jdbcTemplate.update("DELETE FROM car_monitoring.cm_department WHERE id = ?", createdId);
            }
        }
    }

    @Test
    void should_reject_delete_department_with_vehicles() {
        assertThrows(CmConflictException.class,
                () -> departmentAppService.delete(SUPER_ADMIN_ID, DALIAN_DEPARTMENT_ID));
    }

    @Test
    void should_limit_managed_departments_to_scope() {
        List<DepartmentDetailVO> managed = departmentAppService.listManage(OPERATOR_ID, null);

        assertEquals(1, managed.size());
        assertEquals(DALIAN_DEPARTMENT_ID, managed.get(0).getId());
    }

    private long vehicleIdInDepartment(long departmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM car_monitoring.cm_vehicle"
                        + " WHERE department_id = ? AND is_deleted = 0 ORDER BY id LIMIT 1",
                Long.class, departmentId);
    }

    private String departmentName(long departmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM car_monitoring.cm_department WHERE id = ?", String.class, departmentId);
    }

    private List<Long> managedIds() {
        return departmentAppService.listManage(SUPER_ADMIN_ID, null).stream()
                .map(DepartmentDetailVO::getId)
                .toList();
    }
}

