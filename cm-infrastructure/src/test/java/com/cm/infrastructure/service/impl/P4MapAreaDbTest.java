package com.cm.infrastructure.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.persistence.entity.CmMapArea;
import com.cm.infrastructure.persistence.row.MapAreaRow;
import com.cm.infrastructure.service.CmMapAreaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 区域 SQL 真库验证：数据范围过滤、越权 ID 不可见、逻辑删除与 scope 缺失 fail-closed。
 * 默认不跑，设置 CM_P4_DB_TEST=true 启用；事务回滚，不向库内留下测试区域。
 */
@EnabledIfEnvironmentVariable(named = "CM_P4_DB_TEST", matches = "true")
@SpringBootTest(classes = P4MapAreaDbTest.TestApplication.class, properties = {
        "spring.datasource.url=${CM_P3_DB_URL:jdbc:mysql://127.0.0.1:3306/car_monitoring"
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false}",
        "spring.datasource.username=${CM_P3_DB_USERNAME:cm_app}",
        "spring.datasource.password=${CM_P3_DB_PASSWORD:CmApp@2026}",
        "mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml",
        "mybatis-plus.global-config.db-config.logic-delete-field=deleted",
        "mybatis-plus.global-config.db-config.id-type=assign_id"
})
@Transactional
class P4MapAreaDbTest {

    private static final long DALIAN_DEPARTMENT_ID = 100000000117440544L;

    private static final long HUNAN_DEPARTMENT_ID = 100000000117440545L;

    private static final DataScope DALIAN_SCOPE =
            new DataScope(1L, false, List.of(1L), List.of(DALIAN_DEPARTMENT_ID));

    @Autowired
    private CmMapAreaService mapAreaService;

    @Test
    void should_filter_areas_by_department_scope() {
        CmMapArea dalian = area("P4MapAreaDb大连", DALIAN_DEPARTMENT_ID);
        CmMapArea hunan = area("P4MapAreaDb湖南", HUNAN_DEPARTMENT_ID);
        mapAreaService.save(dalian);
        mapAreaService.save(hunan);

        IPage<MapAreaRow> page = mapAreaService.pageByScope(DALIAN_SCOPE, 1, 20, "P4MapAreaDb", null);

        assertEquals(1, page.getTotal(), "范围内只能看到大连中心的区域");
        assertEquals(1, page.getRecords().size());
        assertEquals(dalian.getId(), page.getRecords().get(0).getId());
        assertEquals("大连中心", page.getRecords().get(0).getDepartmentName());
    }

    @Test
    void should_hide_area_outside_scope_by_id() {
        CmMapArea hunan = area("P4MapAreaDb越权", HUNAN_DEPARTMENT_ID);
        mapAreaService.save(hunan);

        assertNull(mapAreaService.getInScope(DALIAN_SCOPE, hunan.getId()));
        assertEquals(hunan.getId(), mapAreaService.getInScope(DataScope.superAdmin(1L), hunan.getId()).getId());
    }

    @Test
    void should_hide_deleted_area() {
        CmMapArea area = area("P4MapAreaDb删除", DALIAN_DEPARTMENT_ID);
        mapAreaService.save(area);

        mapAreaService.removeById(area.getId());

        assertNull(mapAreaService.getInScope(DALIAN_SCOPE, area.getId()));
        IPage<MapAreaRow> page = mapAreaService.pageByScope(DALIAN_SCOPE, 1, 20, "P4MapAreaDb删除", null);
        assertEquals(0, page.getTotal());
    }

    @Test
    void should_fail_closed_without_scope() {
        assertThrows(CmBusinessException.class, () -> mapAreaService.pageByScope(null, 1, 20, null, null));
        assertThrows(CmBusinessException.class, () -> mapAreaService.getInScope(null, 1L));
    }

    @Test
    void should_keep_geometry_and_alarm_flags_round_trip() {
        CmMapArea polygon = area("P4MapAreaDb多边形", DALIAN_DEPARTMENT_ID);
        polygon.setAreaType("polygon");
        polygon.setPoints("121.400000,31.100000;121.600000,31.100000;121.500000,31.300000");
        polygon.setCenterLat(new BigDecimal("31.200000"));
        polygon.setCenterLng(new BigDecimal("121.500000"));
        polygon.setRadius(0);
        polygon.setAlarmEnterDriver(true);
        polygon.setAlarmExitPlatform(true);
        mapAreaService.save(polygon);

        MapAreaRow row = mapAreaService.getInScope(DALIAN_SCOPE, polygon.getId());

        assertEquals("polygon", row.getAreaType());
        assertEquals("121.400000,31.100000;121.600000,31.100000;121.500000,31.300000", row.getPoints());
        assertEquals(0, new BigDecimal("31.200000").compareTo(row.getCenterLat()));
        assertEquals(0, new BigDecimal("121.500000").compareTo(row.getCenterLng()));
        assertEquals(Boolean.TRUE, row.getAlarmEnterDriver());
        assertEquals(Boolean.TRUE, row.getAlarmExitPlatform());
        assertEquals(Boolean.FALSE, row.getAlarmExitDriver());
    }

    private CmMapArea area(String name, long departmentId) {
        CmMapArea area = new CmMapArea();
        area.setCompanyId(0L);
        area.setDepartmentId(departmentId);
        area.setName(name);
        area.setAreaType("circle");
        area.setPoints("121.500000,31.200000");
        area.setCenterLat(new BigDecimal("31.200000"));
        area.setCenterLng(new BigDecimal("121.500000"));
        area.setRadius(500);
        area.setAlarmEnterPlatform(true);
        area.setByTime(false);
        area.setLimitSpeed(false);
        area.setMaxSpeed(BigDecimal.ZERO);
        area.setDelaySeconds(0);
        area.setRemark("db test");
        return area;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.cm.infrastructure.persistence.mapper")
    @Import(CmMapAreaServiceImpl.class)
    static class TestApplication {
    }
}

