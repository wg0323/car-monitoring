package com.cm.admin.service.impl;

import com.cm.admin.config.MonitorProperties;
import com.cm.admin.vo.MonitorMapConfigVO;
import com.cm.domain.exception.CmBusinessException;
import com.cm.infrastructure.persistence.entity.CmSystemConfig;
import com.cm.infrastructure.persistence.entity.CmUser;
import com.cm.infrastructure.persistence.mapper.CmSystemConfigMapper;
import com.cm.infrastructure.persistence.mapper.CmUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapAppServiceImplTest {

    private static final Long USER_ID = 100000000000120201L;

    @Mock
    private CmSystemConfigMapper systemConfigMapper;

    @Mock
    private CmUserMapper userMapper;

    private MonitorProperties properties;

    private MapAppServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new MonitorProperties();
        service = new MapAppServiceImpl(properties, systemConfigMapper, userMapper);
    }

    @Test
    @DisplayName("没配地图 key 时不下发凭据，中心取兜底值")
    void should_report_map_not_configured() {
        when(systemConfigMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectOne(any())).thenReturn(null);

        MonitorMapConfigVO config = service.config(USER_ID);

        assertFalse(config.isConfigured());
        assertEquals("", config.getKey());
        assertEquals("", config.getSecurityCode());
        assertEquals(properties.getMap().getFallbackLat(), config.getCenterLat());
        assertEquals(properties.getMap().getFallbackZoom(), config.getCenterZoom());
    }

    @Test
    @DisplayName("地图中心取自 cm_system_config")
    void should_read_map_center_from_system_config() {
        properties.getMap().setKey("test-key");
        properties.getMap().setSecurityCode("test-code");
        when(systemConfigMapper.selectList(any())).thenReturn(List.of(
                config("initLat", "31.2"), config("initLng", "121.5"), config("initZoomLevel", "12")));
        when(userMapper.selectOne(any())).thenReturn(null);

        MonitorMapConfigVO config = service.config(USER_ID);

        assertTrue(config.isConfigured());
        assertEquals("test-key", config.getKey());
        assertEquals(31.2, config.getCenterLat());
        assertEquals(121.5, config.getCenterLng());
        assertEquals(12, config.getCenterZoom());
    }

    @Test
    @DisplayName("已保存的个人中心覆盖系统默认中心与级别")
    void should_override_center_with_user_preference() {
        when(systemConfigMapper.selectList(any())).thenReturn(List.of(
                config("initLat", "31.2"), config("initLng", "121.5"), config("initZoomLevel", "12")));
        when(userMapper.selectOne(any())).thenReturn(userOf("38.914004", "121.614682", 15));

        MonitorMapConfigVO config = service.config(USER_ID);

        assertEquals(38.914004, config.getCenterLat(), 0.000001D);
        assertEquals(121.614682, config.getCenterLng(), 0.000001D);
        assertEquals(15, config.getCenterZoom());
    }

    @Test
    @DisplayName("个人中心为 0,0 时视为未保存，保持系统中心")
    void should_ignore_unset_user_preference() {
        when(systemConfigMapper.selectList(any())).thenReturn(List.of(
                config("initLat", "31.2"), config("initLng", "121.5"), config("initZoomLevel", "12")));
        when(userMapper.selectOne(any())).thenReturn(userOf("0", "0", 0));

        MonitorMapConfigVO config = service.config(USER_ID);

        assertEquals(31.2, config.getCenterLat());
        assertEquals(121.5, config.getCenterLng());
        assertEquals(12, config.getCenterZoom());
    }

    @Test
    @DisplayName("个人中心为 0,0 时拒绝保存")
    void should_reject_zero_center() {
        assertThrows(CmBusinessException.class, () -> service.savePreference(USER_ID, 0, 0, 12));
    }

    @Test
    @DisplayName("缩放级别越界时拒绝保存")
    void should_reject_invalid_zoom() {
        assertThrows(CmBusinessException.class, () -> service.savePreference(USER_ID, 38.9, 121.6, 2));
        assertThrows(CmBusinessException.class, () -> service.savePreference(USER_ID, 38.9, 121.6, 21));
    }

    @Test
    @DisplayName("保存偏好写入 cm_user 的中心、级别与引擎")
    void should_persist_preference_to_user() {
        when(userMapper.updateById(any(CmUser.class))).thenReturn(1);

        service.savePreference(USER_ID, 38.9140044, 121.6146824, 15);

        ArgumentCaptor<CmUser> captor = ArgumentCaptor.forClass(CmUser.class);
        verify(userMapper).updateById(captor.capture());
        CmUser patch = captor.getValue();
        assertEquals(USER_ID, patch.getId());
        assertEquals(0, new BigDecimal("38.914004").compareTo(patch.getMapCenterLat()));
        assertEquals(0, new BigDecimal("121.614682").compareTo(patch.getMapCenterLng()));
        assertEquals(15, patch.getMapLevel());
        assertEquals("amap", patch.getMapType());
    }

    @Test
    @DisplayName("账号不存在时保存失败并报错")
    void should_fail_when_user_missing() {
        when(userMapper.updateById(any(CmUser.class))).thenReturn(0);

        assertThrows(CmBusinessException.class, () -> service.savePreference(USER_ID, 38.9, 121.6, 12));
    }

    private CmUser userOf(String lat, String lng, int level) {
        CmUser user = new CmUser();
        user.setId(USER_ID);
        user.setMapCenterLat(new BigDecimal(lat));
        user.setMapCenterLng(new BigDecimal(lng));
        user.setMapLevel(level);
        return user;
    }

    private CmSystemConfig config(String key, String value) {
        CmSystemConfig config = new CmSystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }
}
