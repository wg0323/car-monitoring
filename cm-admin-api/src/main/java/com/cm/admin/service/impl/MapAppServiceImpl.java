package com.cm.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cm.admin.config.MonitorProperties;
import com.cm.admin.service.MapAppService;
import com.cm.admin.vo.MonitorMapConfigVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.infrastructure.persistence.entity.CmSystemConfig;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.mapper.CmSystemConfigMapper;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图配置实现。中心点优先级：当前账号保存的个人中心 > cm_system_config 的全局中心 > 兜底值。
 * 与旧系统 setMapCenter 一致：保存一次后，所有地图页面都回到这个中心。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapAppServiceImpl implements MapAppService {

    private static final String CONFIG_INIT_LAT = "initLat";

    private static final String CONFIG_INIT_LNG = "initLng";

    private static final String CONFIG_INIT_ZOOM = "initZoomLevel";

    private static final int MIN_ZOOM = 3;

    private static final int MAX_ZOOM = 20;

    private static final int COORD_SCALE = 6;

    private final MonitorProperties properties;

    private final CmSystemConfigMapper systemConfigMapper;

    private final CmUserMapper userMapper;

    @Override
    public MonitorMapConfigVO config(Long userId) {
        MonitorMapConfigVO vo = new MonitorMapConfigVO();
        vo.setEngine(properties.getMap().getEngine());
        boolean configured = !properties.getMap().getKey().isBlank();
        vo.setConfigured(configured);
        // 未配置时不下发任何凭据字段，避免前端拿到空 key 还去加载 SDK 再报一串脚本错误。
        vo.setKey(configured ? properties.getMap().getKey() : "");
        vo.setSecurityCode(configured ? properties.getMap().getSecurityCode() : "");
        Map<String, String> config = loadSystemConfig();
        vo.setCenterLat(parseDouble(config.get(CONFIG_INIT_LAT), properties.getMap().getFallbackLat()));
        vo.setCenterLng(parseDouble(config.get(CONFIG_INIT_LNG), properties.getMap().getFallbackLng()));
        vo.setCenterZoom(parseInt(config.get(CONFIG_INIT_ZOOM), properties.getMap().getFallbackZoom()));
        applyPreference(userId, vo);
        return vo;
    }

    @Override
    public void savePreference(Long userId, double centerLat, double centerLng, int zoom) {
        requireValidPreference(centerLat, centerLng, zoom);
        CmUser patch = new CmUser();
        patch.setId(userId);
        patch.setMapCenterLat(toCoordinate(centerLat));
        patch.setMapCenterLng(toCoordinate(centerLng));
        patch.setMapLevel(zoom);
        patch.setMapType(properties.getMap().getEngine());
        int updated = userMapper.updateById(patch);
        if (updated == 0) {
            throw new CmBusinessException("账号不存在，无法保存地图中心");
        }
        log.info("地图中心已保存, userId={}, lat={}, lng={}, zoom={}", userId, centerLat, centerLng, zoom);
    }

    /** 个人中心只有经纬度都有效时才覆盖系统默认值；缩放级别非法时保持系统值。 */
    private void applyPreference(Long userId, MonitorMapConfigVO vo) {
        CmUser user = findUser(userId);
        if (user == null || user.getMapCenterLat() == null || user.getMapCenterLng() == null) {
            return;
        }
        double lat = user.getMapCenterLat().doubleValue();
        double lng = user.getMapCenterLng().doubleValue();
        if (!isUsableCenter(lat, lng)) {
            return;
        }
        vo.setCenterLat(lat);
        vo.setCenterLng(lng);
        Integer level = user.getMapLevel();
        if (level != null && level >= MIN_ZOOM && level <= MAX_ZOOM) {
            vo.setCenterZoom(level);
        }
    }

    private CmUser findUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<CmUser>()
                .eq(CmUser::getId, userId)
                .eq(CmUser::getDeleted, false));
    }

    private void requireValidPreference(double centerLat, double centerLng, int zoom) {
        if (!isUsableCenter(centerLat, centerLng)) {
            throw new CmBusinessException("地图中心不能为 0,0，请先移动到目标位置");
        }
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new CmBusinessException("缩放级别必须在 " + MIN_ZOOM + "~" + MAX_ZOOM + " 之间");
        }
    }

    /** (0,0) 是 cm_user 的默认值，代表从未保存过，不能当作有效中心。 */
    private boolean isUsableCenter(double lat, double lng) {
        return Math.abs(lat) > 0.000001D || Math.abs(lng) > 0.000001D;
    }

    private BigDecimal toCoordinate(double value) {
        return BigDecimal.valueOf(value).setScale(COORD_SCALE, RoundingMode.HALF_UP);
    }

    private Map<String, String> loadSystemConfig() {
        List<CmSystemConfig> rows = systemConfigMapper.selectList(new LambdaQueryWrapper<CmSystemConfig>()
                .in(CmSystemConfig::getConfigKey, List.of(CONFIG_INIT_LAT, CONFIG_INIT_LNG, CONFIG_INIT_ZOOM)));
        Map<String, String> config = new LinkedHashMap<>(rows.size());
        for (CmSystemConfig row : rows) {
            if (row.getConfigKey() != null && row.getConfigValue() != null) {
                config.put(row.getConfigKey(), row.getConfigValue());
            }
        }
        return config;
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("地图中心配置不是数字，按兜底值处理, value={}", value);
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("地图缩放配置不是整数，按兜底值处理, value={}", value);
            return fallback;
        }
    }
}
