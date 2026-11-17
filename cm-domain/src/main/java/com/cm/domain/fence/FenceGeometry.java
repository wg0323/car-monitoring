package com.cm.domain.fence;

import com.cm.domain.exception.CmBusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 区域几何：点串解析、按类型校验、外接矩形中心计算与归一化输出。
 *
 * <p>点串沿用旧系统格式 {@code lng,lat;lng,lat}，圆形只存圆心一点、矩形存四个角点、多边形至少三点。
 * 中心点按外接矩形计算，与旧系统 getPolygonCenter 的算法一致，前端拿到的 center 与库内回填值同源。
 */
public final class FenceGeometry {

    public static final int MAX_POINTS = 100;

    public static final int MIN_POLYGON_POINTS = 3;

    public static final int MIN_RADIUS_METERS = 1;

    public static final int MAX_RADIUS_METERS = 50000;

    public static final int RECT_POINTS = 4;

    private static final double COORD_TOLERANCE = 0.000001D;

    private static final double MIN_LNG = -180D;

    private static final double MAX_LNG = 180D;

    private static final double MIN_LAT = -90D;

    private static final double MAX_LAT = 90D;

    private static final String POINT_SEPARATOR = ";";

    private static final String COORD_SEPARATOR = ",";

    private static final String POINT_FORMAT = "%.6f,%.6f";

    private static final int COORD_PARTS = 2;

    private final FenceAreaType areaType;

    private final List<Point> points;

    private final Point center;

    private final int radiusMeters;

    private FenceGeometry(FenceAreaType areaType, List<Point> points, Point center, int radiusMeters) {
        this.areaType = areaType;
        this.points = List.copyOf(points);
        this.center = center;
        this.radiusMeters = radiusMeters;
    }

    /**
     * 解析并校验几何。非圆形区域丢弃传入半径，统一落 0，避免库内出现「矩形带半径」的矛盾数据。
     */
    public static FenceGeometry parse(String areaType, String points, Integer radius) {
        FenceAreaType type = FenceAreaType.fromCode(areaType);
        List<Point> parsed = parsePoints(points);
        requirePointCount(type, parsed);
        int radiusMeters = type == FenceAreaType.CIRCLE ? requireRadius(radius) : 0;
        return new FenceGeometry(type, parsed, boundingCenter(parsed), radiusMeters);
    }

    private static List<Point> parsePoints(String points) {
        if (points == null || points.isBlank()) {
            throw new CmBusinessException("区域边界点不能为空");
        }
        List<Point> parsed = new ArrayList<>();
        for (String segment : points.split(POINT_SEPARATOR)) {
            if (segment.isBlank()) {
                continue;
            }
            String[] parts = segment.split(COORD_SEPARATOR);
            if (parts.length != COORD_PARTS) {
                throw new CmBusinessException("区域边界点格式非法，应为 lng,lat");
            }
            parsed.add(toPoint(parts[0], parts[1]));
            if (parsed.size() > MAX_POINTS) {
                throw new CmBusinessException("区域边界点最多 " + MAX_POINTS + " 个");
            }
        }
        if (parsed.isEmpty()) {
            throw new CmBusinessException("区域边界点不能为空");
        }
        return parsed;
    }

    private static Point toPoint(String lngText, String latText) {
        double lng;
        double lat;
        try {
            lng = Double.parseDouble(lngText.trim());
            lat = Double.parseDouble(latText.trim());
        } catch (NumberFormatException ex) {
            throw new CmBusinessException("区域边界点必须是数字");
        }
        if (!Double.isFinite(lng) || !Double.isFinite(lat)) {
            throw new CmBusinessException("区域边界点必须是有限数字");
        }
        if (lng < MIN_LNG || lng > MAX_LNG || lat < MIN_LAT || lat > MAX_LAT) {
            throw new CmBusinessException("区域边界点超出经纬度范围");
        }
        if (Math.abs(lng) <= COORD_TOLERANCE && Math.abs(lat) <= COORD_TOLERANCE) {
            throw new CmBusinessException("区域边界点不能是 0,0");
        }
        return new Point(lng, lat);
    }

    private static void requirePointCount(FenceAreaType type, List<Point> points) {
        switch (type) {
            case CIRCLE -> requireSinglePoint(points);
            case RECT -> requireRect(points);
            case POLYGON -> requirePolygon(points);
            default -> throw new CmBusinessException("不支持的区域类型：" + type.getCode());
        }
    }

    private static void requireSinglePoint(List<Point> points) {
        if (points.size() != 1) {
            throw new CmBusinessException("圆形区域只接受一个圆心点");
        }
    }

    private static void requireRect(List<Point> points) {
        if (points.size() != RECT_POINTS) {
            throw new CmBusinessException("矩形区域必须是 4 个角点");
        }
        int lngCount = distinctCount(points, true);
        int latCount = distinctCount(points, false);
        if (lngCount != 2 || latCount != 2) {
            throw new CmBusinessException("矩形区域四个角点未落在两条经线与两条纬线上");
        }
    }

    private static void requirePolygon(List<Point> points) {
        if (points.size() < MIN_POLYGON_POINTS) {
            throw new CmBusinessException("多边形区域至少 " + MIN_POLYGON_POINTS + " 个点");
        }
        for (int index = 1; index < points.size(); index++) {
            if (samePoint(points.get(index - 1), points.get(index))) {
                throw new CmBusinessException("多边形区域不能包含相邻重复点");
            }
        }
    }

    private static int requireRadius(Integer radius) {
        if (radius == null || radius < MIN_RADIUS_METERS || radius > MAX_RADIUS_METERS) {
            throw new CmBusinessException("圆形半径必须在 " + MIN_RADIUS_METERS + "~" + MAX_RADIUS_METERS + " 米之间");
        }
        return radius;
    }

    private static int distinctCount(List<Point> points, boolean byLng) {
        List<Double> distinct = new ArrayList<>();
        for (Point point : points) {
            double value = byLng ? point.lng() : point.lat();
            boolean seen = false;
            for (double existing : distinct) {
                if (Math.abs(existing - value) <= COORD_TOLERANCE) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                distinct.add(value);
            }
        }
        return distinct.size();
    }

    private static boolean samePoint(Point left, Point right) {
        return Math.abs(left.lng() - right.lng()) <= COORD_TOLERANCE
                && Math.abs(left.lat() - right.lat()) <= COORD_TOLERANCE;
    }

    /** 外接矩形中心；单点时即该点本身，与旧系统 getPolygonCenter 行为一致。 */
    public static Point boundingCenter(List<Point> points) {
        if (points.isEmpty()) {
            throw new CmBusinessException("区域边界点不能为空");
        }
        double minLng = points.get(0).lng();
        double maxLng = minLng;
        double minLat = points.get(0).lat();
        double maxLat = minLat;
        for (Point point : points) {
            minLng = Math.min(minLng, point.lng());
            maxLng = Math.max(maxLng, point.lng());
            minLat = Math.min(minLat, point.lat());
            maxLat = Math.max(maxLat, point.lat());
        }
        return new Point((minLng + maxLng) / 2, (minLat + maxLat) / 2);
    }

    /** 入库前的归一化点串：统一保留 6 位小数，避免同一区域因入参精度不同产生两份字符串。 */
    public String normalizedPoints() {
        StringBuilder builder = new StringBuilder(points.size() * 24);
        for (Point point : points) {
            if (builder.length() > 0) {
                builder.append(POINT_SEPARATOR);
            }
            builder.append(String.format(Locale.ROOT, POINT_FORMAT, point.lng(), point.lat()));
        }
        return builder.toString();
    }

    public FenceAreaType getAreaType() {
        return areaType;
    }

    public List<Point> getPoints() {
        return points;
    }

    public Point getCenter() {
        return center;
    }

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public record Point(double lng, double lat) {
    }
}
