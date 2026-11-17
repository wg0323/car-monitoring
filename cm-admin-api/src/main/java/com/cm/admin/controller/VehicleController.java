package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.VehicleAppService;
import com.cm.admin.vo.VehicleDetailVO;
import com.cm.admin.vo.VehicleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "车辆", description = "车辆基础资料分页查询，结果受服务端数据范围约束")
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final VehicleAppService vehicleAppService;

    @Operation(summary = "分页查询车辆",
            description = "total 为按当前账号数据范围过滤后的总行数；departmentId 越界返回 403；"
                    + "online 为 true 只返回在线车，false 只返回离线车，空表示不筛")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无车辆查看权限，或所选部门不在数据范围内")
    @PreAuthorize("hasAuthority('vehicle:view')")
    @GetMapping
    public ApiResult<PageResult<VehicleVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "终端 SIM 前缀，空表示不过滤")
            @RequestParam(required = false) String simNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "在线筛选：true 只查在线，false 只查离线，空表示不筛")
            @RequestParam(required = false) Boolean online) {
        return ApiResult.ok(vehicleAppService.page(principal.getUserId(), page, size, plateNo, simNo,
                departmentId, online));
    }

    @Operation(summary = "查询车辆详情",
            description = "返回 cm_vehicle 全量基础资料与所属部门、终端编号；越界与不存在统一返回 403")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无车辆查看权限，或目标车辆不在数据范围内")
    @PreAuthorize("hasAuthority('vehicle:view')")
    @GetMapping("/{id}")
    public ApiResult<VehicleDetailVO> detail(@AuthenticationPrincipal CmUserDetails principal,
                                             @Parameter(description = "车辆 ID", required = true)
                                             @PathVariable long id) {
        return ApiResult.ok(vehicleAppService.detail(principal.getUserId(), id));
    }
}
