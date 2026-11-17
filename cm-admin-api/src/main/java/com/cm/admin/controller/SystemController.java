package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.service.SystemInfoService;
import com.cm.admin.vo.SystemInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统信息", description = "系统探针：返回应用名、升级阶段、Profile 与 JDK 版本")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemInfoService systemInfoService;

    @Operation(summary = "查询系统骨架信息", description = "无需登录态；用于验证工程可独立启动、配置外置生效与中文编码正常")
    @ApiResponse(responseCode = "200", description = "查询成功，返回应用元信息")
    @GetMapping("/info")
    public ApiResult<SystemInfoVO> info() {
        return ApiResult.ok(systemInfoService.describe());
    }
}
