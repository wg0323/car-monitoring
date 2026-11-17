package com.cm.domain.fence;

import com.cm.domain.exception.CmBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 区域几何：三种类型的点数约束、点串解析、外接中心与归一化输出。
 */
class FenceGeometryTest {

    @Test
    @DisplayName("圆形只接受一个圆心点并保留半径")
    void should_parse_circle_center_and_radius() {
        FenceGeometry geometry = FenceGeometry.parse("circle", "121.500000,31.200000", 800);

        assertEquals(FenceAreaType.CIRCLE, geometry.getAreaType());
        assertEquals(1, geometry.getPoints().size());
        assertEquals(800, geometry.getRadiusMeters());
        assertEquals(121.5D, geometry.getCenter().lng(), 0.000001D);
        assertEquals(31.2D, geometry.getCenter().lat(), 0.000001D);
    }

    @Test
    @DisplayName("圆形传多个点被拒绝")
    void should_reject_circle_with_multi_points() {
        assertThrows(CmBusinessException.class,
                () -> FenceGeometry.parse("circle", "121.5,31.2;121.6,31.3", 500));
    }

    @Test
    @DisplayName("圆形半径为 0、超上限或缺失都被拒绝")
    void should_reject_invalid_circle_radius() {
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "121.5,31.2", 0));
        assertThrows(CmBusinessException.class,
                () -> FenceGeometry.parse("circle", "121.5,31.2", FenceGeometry.MAX_RADIUS_METERS + 1));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "121.5,31.2", null));
    }

    @Test
    @DisplayName("矩形四角点解析成功且中心为两条对角线的交点")
    void should_parse_rect_and_compute_center() {
        FenceGeometry geometry = FenceGeometry.parse("rect",
                "121.400000,31.100000;121.600000,31.100000;121.600000,31.300000;121.400000,31.300000", null);

        assertEquals(4, geometry.getPoints().size());
        assertEquals(0, geometry.getRadiusMeters());
        assertEquals(121.5D, geometry.getCenter().lng(), 0.000001D);
        assertEquals(31.2D, geometry.getCenter().lat(), 0.000001D);
    }

    @Test
    @DisplayName("矩形四个角点未对齐到两条经纬线时被拒绝")
    void should_reject_rect_not_aligned() {
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("rect",
                "121.400000,31.100000;121.600000,31.100000;121.550000,31.300000;121.400000,31.300000", null));
    }

    @Test
    @DisplayName("矩形点数不是 4 个时被拒绝")
    void should_reject_rect_with_wrong_point_count() {
        assertThrows(CmBusinessException.class,
                () -> FenceGeometry.parse("rect", "121.4,31.1;121.6,31.1;121.6,31.3", null));
    }

    @Test
    @DisplayName("多边形三点起可保存，中心取外接矩形中心")
    void should_parse_polygon_and_compute_bounding_center() {
        FenceGeometry geometry = FenceGeometry.parse("polygon",
                "121.400000,31.100000;121.700000,31.150000;121.500000,31.400000", null);

        assertEquals(3, geometry.getPoints().size());
        assertEquals(121.55D, geometry.getCenter().lng(), 0.000001D);
        assertEquals(31.25D, geometry.getCenter().lat(), 0.000001D);
    }

    @Test
    @DisplayName("多边形少于三点或存在相邻重复点被拒绝")
    void should_reject_invalid_polygon() {
        assertThrows(CmBusinessException.class,
                () -> FenceGeometry.parse("polygon", "121.4,31.1;121.6,31.1", null));
        assertThrows(CmBusinessException.class,
                () -> FenceGeometry.parse("polygon", "121.4,31.1;121.4,31.1;121.6,31.3", null));
    }

    @Test
    @DisplayName("点串格式非法时给出可读错误")
    void should_reject_malformed_points() {
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "", 100));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "121.5", 100));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "121.5,31.2,0", 100));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "abc,31.2", 100));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "181,31.2", 100));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "0,0", 100));
        assertThrows(CmBusinessException.class, () -> FenceGeometry.parse("circle", "NaN,31.2", 100));
    }

    @Test
    @DisplayName("点数超过上限时被拒绝")
    void should_reject_too_many_points() {
        StringBuilder builder = new StringBuilder("121.400000,31.100000");
        for (int index = 0; index < FenceGeometry.MAX_POINTS; index++) {
            builder.append(';').append(121.41D + index * 0.001D).append(",31.11");
        }

        assertThrows(CmBusinessException.class,
                () -> FenceGeometry.parse("polygon", builder.toString(), null));
    }

    @Test
    @DisplayName("归一化点串统一保留 6 位小数")
    void should_normalize_points_to_six_decimals() {
        FenceGeometry circle = FenceGeometry.parse("circle", "121.5,31.2", 300);
        FenceGeometry polygon = FenceGeometry.parse("polygon",
                "121.4,31.1;121.6,31.1;121.5,31.3", null);

        assertEquals("121.500000,31.200000", circle.normalizedPoints());
        assertEquals("121.400000,31.100000;121.600000,31.100000;121.500000,31.300000",
                polygon.normalizedPoints());
    }
}
