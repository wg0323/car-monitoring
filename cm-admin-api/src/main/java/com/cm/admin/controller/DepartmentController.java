package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.DepartmentAppService;
import com.cm.admin.vo.DepartmentDetailVO;
import com.cm.admin.vo.DepartmentSaveRequest;
import com.cm.admin.vo.DepartmentVO;
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

import java.util.List;

@Tag(name = "部门", description = "数据范围内的部门下拉与车辆分组管理（TC-ADM-05）")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentAppService departmentAppService;

    @Operation(summary = "查询可访问部门", description = "超管返回全部部门，非超管只返回 cm_user_department 中已授权的部门")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @GetMapping
    public ApiResult<List<DepartmentVO>> list(@AuthenticationPrincipal CmUserDetails principal) {
        return ApiResult.ok(departmentAppService.list(principal.getUserId()));
    }

    @Operation(summary = "查询车辆分组管理树表",
            description = "返回数据范围内的全部分组，含上级名称与旧 editDep 表单字段；keyword 按名称前缀匹配")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @PreAuthorize("hasAuthority('organization:view')")
    @GetMapping("/manage")
    public ApiResult<List<DepartmentDetailVO>> listManage(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车组名称前缀，空表示不过滤")
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(departmentAppService.listManage(principal.getUserId(), keyword));
    }

    @Operation(summary = "新增车辆分组",
            description = "名称全局唯一（含已删除分组）；上级必须落在数据范围内；新增后自动授权给创建人")
    @ApiResponse(responseCode = "200", description = "新增成功")
    @ApiResponse(responseCode = "400", description = "名称重复或上级不合法")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无新增权限，或上级车组不在数据范围内")
    @PreAuthorize("hasAuthority('organization:department:create')")
    @PostMapping
    public ApiResult<DepartmentDetailVO> create(@AuthenticationPrincipal CmUserDetails principal,
                                                @Valid @RequestBody DepartmentSaveRequest request) {
        return ApiResult.ok(departmentAppService.create(principal.getUserId(), request));
    }

    @Operation(summary = "修改车辆分组",
            description = "校验规则与新增一致；上级不能是自己或自己的下级；修改后回读库内最新行")
    @ApiResponse(responseCode = "200", description = "修改成功")
    @ApiResponse(responseCode = "400", description = "名称重复或上级不合法")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无修改权限，或目标车组不在数据范围内")
    @PreAuthorize("hasAuthority('organization:department:update')")
    @PutMapping("/{id}")
    public ApiResult<DepartmentDetailVO> update(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车组 ID", required = true) @PathVariable long id,
            @Valid @RequestBody DepartmentSaveRequest request) {
        return ApiResult.ok(departmentAppService.update(principal.getUserId(), id, request));
    }

    @Operation(summary = "删除车辆分组",
            description = "逻辑删除；有车辆或有子组时返回 409，与旧系统 fakeDelete 一致")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无删除权限，或目标车组不在数据范围内")
    @ApiResponse(responseCode = "409", description = "车组下仍有车辆或子部门")
    @PreAuthorize("hasAuthority('organization:department:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@AuthenticationPrincipal CmUserDetails principal,
                                  @Parameter(description = "车组 ID", required = true) @PathVariable long id) {
        departmentAppService.delete(principal.getUserId(), id);
        return ApiResult.ok(null);
    }
}
