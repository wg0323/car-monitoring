package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.MapAppService;
import com.cm.admin.service.MonitorAppService;
import com.cm.admin.vo.MonitorMapConfigVO;
import com.cm.admin.vo.MonitorSummaryVO;
import com.cm.admin.vo.MonitorVehicleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "实时监控", description = "车辆最新位置、在线状态与地图配置；实时推送走 WebSocket /ws/monitor")
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final MonitorAppService monitorAppService;

    private final MapAppService mapAppService;

    @Operation(summary = "分页查询实时监控行",
            description = "每行带最新位置与在线态：位置来自 Redis 最新位置，在线态来自在线租约；"
                    + "total 与车辆列表同一份数据范围，departmentId 越界返回 403；"
                    + "online 为 true 只返回在线车，false 只返回离线车，空表示不筛")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无实时监控权限，或所选部门不在数据范围内")
    @PreAuthorize("hasAuthority('monitor:view')")
    @GetMapping("/vehicles")
    public ApiResult<PageResult<MonitorVehicleVO>> vehicles(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "在线筛选：true 只查在线，false 只查离线，空表示不筛")
            @RequestParam(required = false) Boolean online) {
        return ApiResult.ok(monitorAppService.page(principal.getUserId(), page, size, keyword, departmentId, online));
    }

    @Operation(summary = "查询实时监控状态栏",
            description = "按当前账号数据范围统计总数、在线、离线、停车与上线率；结果服务端缓存数秒")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无实时监控权限")
    @PreAuthorize("hasAuthority('monitor:view')")
    @GetMapping("/summary")
    public ApiResult<MonitorSummaryVO> summary(@AuthenticationPrincipal CmUserDetails principal) {
        return ApiResult.ok(monitorAppService.summary(principal.getUserId()));
    }

    @Operation(summary = "查询地图配置",
            description = "下发地图引擎标识、key 与初始中心。已保存个人中心时优先返回个人中心；"
                    + "key 由服务端环境变量注入，未配置时 configured=false 且不下发凭据")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无实时监控权限")
    @PreAuthorize("hasAuthority('monitor:view')")
    @GetMapping("/map-config")
    public ApiResult<MonitorMapConfigVO> mapConfig(@AuthenticationPrincipal CmUserDetails principal) {
        return ApiResult.ok(mapAppService.config(principal.getUserId()));
    }
}
