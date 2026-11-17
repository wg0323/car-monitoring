package com.cm.admin.service;

import com.cm.admin.vo.MonitorMapConfigVO;

/**
 * 地图配置与个人中心点。从 MonitorAppService 抽出，围栏、监控、轨迹三个页面共用同一份配置口径。
 */
public interface MapAppService {

    /**
     * 地图引擎、key 与初始中心。已保存个人中心时覆盖系统默认中心。
     */
    MonitorMapConfigVO config(Long userId);

    /**
     * 保存当前账号的默认中心，影响之后打开的所有地图页面。
     */
    void savePreference(Long userId, double centerLat, double centerLng, int zoom);
}
