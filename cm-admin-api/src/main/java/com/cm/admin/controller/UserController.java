package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.UserAppService;
import com.cm.admin.vo.UserDetailVO;
import com.cm.admin.vo.UserVO;
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

@Tag(name = "用户", description = "组织权限域的用户只读查询，结果受服务端数据范围约束")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final UserAppService userAppService;

    @Operation(summary = "分页查询用户",
            description = "total 为按当前账号数据范围过滤后的总行数：只统计与被查看用户有共同可访问部门的记录，"
                    + "超管返回全量；departmentId 越界返回 403")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无组织权限菜单权限，或所选部门不在数据范围内")
    @PreAuthorize("hasAuthority('organization:view')")
    @GetMapping
    public ApiResult<PageResult<UserVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "登录名或用户名称前缀，空表示不过滤")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId) {
        return ApiResult.ok(userAppService.page(principal.getUserId(), page, size, keyword, departmentId));
    }

    @Operation(summary = "查询用户详情",
            description = "返回基本信息、角色列表与可访问部门；目标用户不在数据范围内时返回 403，不区分其是否存在")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无用户详情按钮权限，或目标用户不在数据范围内")
    @PreAuthorize("hasAuthority('organization:user:detail')")
    @GetMapping("/{id}")
    public ApiResult<UserDetailVO> detail(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "目标用户 ID", required = true)
            @PathVariable Long id) {
        return ApiResult.ok(userAppService.detail(principal.getUserId(), id));
    }
}
