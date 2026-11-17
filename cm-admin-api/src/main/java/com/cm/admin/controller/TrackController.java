package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.TrackAppService;
import com.cm.admin.vo.TrackResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag(name = "历史轨迹", description = "单车历史轨迹查询、回放数据与 Excel 导出")
@RestController
@RequestMapping("/api/v1/tracks")
@RequiredArgsConstructor
public class TrackController {

    private static final String PERMISSION = "hasAuthority('track:view')";

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TrackAppService trackAppService;

    @Operation(summary = "查询历史轨迹",
            description = "时间跨度不超过 3 天，点数不超过 2 万；一次返回全部点供表格、地图与回放共用")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无轨迹权限，或所选车辆不在数据范围内")
    @PreAuthorize(PERMISSION)
    @GetMapping
    public ApiResult<TrackResultVO> query(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车辆 ID", required = true, example = "1000000000000020001")
            @RequestParam long vehicleId,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResult.ok(trackAppService.query(principal.getUserId(), vehicleId, startTime, endTime));
    }

    @Operation(summary = "导出历史轨迹", description = "与查询同一条件，导出列对齐旧系统 GPS 历史轨迹")
    @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流")
    @PreAuthorize(PERMISSION)
    @GetMapping("/export")
    public void export(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车辆 ID", required = true)
            @RequestParam long vehicleId,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        prepareExcelResponse(response);
        trackAppService.export(principal.getUserId(), vehicleId, startTime, endTime, response.getOutputStream());
    }

    private void prepareExcelResponse(HttpServletResponse response) {
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = "GPS历史轨迹_" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }
}
