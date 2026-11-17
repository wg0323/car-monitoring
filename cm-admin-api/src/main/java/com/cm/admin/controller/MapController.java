package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.MapAppService;
import com.cm.admin.vo.MapPreferenceRequest;
import com.cm.admin.vo.MapPreferenceVO;
import com.cm.admin.vo.MonitorMapConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "地图配置", description = "地图引擎与 key、当前账号默认中心。登录即可访问，不绑定具体功能域权限")
@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class MapController {

    private final MapAppService mapAppService;

    @Operation(summary = "查询地图配置",
            description = "返回引擎、key 与初始中心。已保存个人中心时优先返回个人中心；"
                    + "未配置 key 时 configured=false 且不下发凭据")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @GetMapping("/config")
    public ApiResult<MonitorMapConfigVO> config(@AuthenticationPrincipal CmUserDetails principal) {
        return ApiResult.ok(mapAppService.config(principal.getUserId()));
    }

    @Operation(summary = "保存当前账号默认地图中心",
            description = "保存后实时监控、历史轨迹与围栏页面都从这里开始。坐标为 GCJ-02，缩放级别 3~20")
    @ApiResponse(responseCode = "200", description = "保存成功")
    @ApiResponse(responseCode = "400", description = "中心为 0,0 或缩放级别越界")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @PutMapping("/preference")
    public ApiResult<MapPreferenceVO> savePreference(
            @AuthenticationPrincipal CmUserDetails principal,
            @Valid @RequestBody MapPreferenceRequest request) {
        mapAppService.savePreference(principal.getUserId(),
                request.getCenterLat(), request.getCenterLng(), request.getZoom());
        MapPreferenceVO vo = new MapPreferenceVO();
        vo.setCenterLat(request.getCenterLat());
        vo.setCenterLng(request.getCenterLng());
        vo.setZoom(request.getZoom());
        vo.setSaved(true);
        return ApiResult.ok(vo);
    }
}
