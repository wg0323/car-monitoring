package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.converter.FenceConverter;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.FenceAppService;
import com.cm.admin.vo.FenceSaveRequest;
import com.cm.admin.vo.FenceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "围栏区域", description = "圆形/矩形/多边形区域的绘制保存、列表、编辑与删除；结果受服务端数据范围约束")
@RestController
@RequestMapping("/api/v1/fences")
@RequiredArgsConstructor
public class FenceController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final FenceAppService fenceAppService;

    @Operation(summary = "分页查询区域",
            description = "total 为按当前账号数据范围过滤后的总行数；departmentId 越界返回 403")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无区域查看权限，或所选部门不在数据范围内")
    @PreAuthorize("hasAuthority('fence:view')")
    @GetMapping
    public ApiResult<PageResult<FenceVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "区域名称前缀，空表示不过滤") @RequestParam(required = false) String name,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId) {
        return ApiResult.ok(fenceAppService.page(principal.getUserId(), page, size, name, departmentId));
    }

    @Operation(summary = "查询区域详情", description = "区域不在当前账号数据范围内时返回业务错误")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无区域查看权限")
    @PreAuthorize("hasAuthority('fence:view')")
    @GetMapping("/{id}")
    public ApiResult<FenceVO> get(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "区域 ID", required = true) @PathVariable long id) {
        return ApiResult.ok(fenceAppService.get(principal.getUserId(), id));
    }

    @Operation(summary = "新建区域",
            description = "几何由地图绘制产生；圆形只传圆心与半径，矩形四个角点，多边形至少三个点。"
                    + "报警类型至少选一种，时间窗与限速开关打开时对应参数必填")
    @ApiResponse(responseCode = "200", description = "创建成功，返回含 ID 与部门名的完整行")
    @ApiResponse(responseCode = "400", description = "参数校验失败或几何不合法")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无区域新建权限，或所选部门不在数据范围内")
    @PreAuthorize("hasAuthority('fence:create')")
    @PostMapping
    public ApiResult<FenceVO> create(
            @AuthenticationPrincipal CmUserDetails principal,
            @Valid @RequestBody FenceSaveRequest request) {
        return ApiResult.ok(fenceAppService.create(principal.getUserId(), FenceConverter.toCommand(request)));
    }

    @Operation(summary = "修改区域", description = "校验规则与新建一致；修改后回读库内最新行")
    @ApiResponse(responseCode = "200", description = "修改成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败或几何不合法")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无区域修改权限，或所选部门不在数据范围内")
    @PreAuthorize("hasAuthority('fence:update')")
    @PutMapping("/{id}")
    public ApiResult<FenceVO> update(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "区域 ID", required = true) @PathVariable long id,
            @Valid @RequestBody FenceSaveRequest request) {
        return ApiResult.ok(fenceAppService.update(principal.getUserId(), id, FenceConverter.toCommand(request)));
    }

    @Operation(summary = "删除区域", description = "逻辑删除；区域不在当前账号数据范围内时不执行任何写入")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无区域删除权限")
    @PreAuthorize("hasAuthority('fence:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "区域 ID", required = true) @PathVariable long id) {
        fenceAppService.delete(principal.getUserId(), id);
        return ApiResult.ok(null);
    }
}
