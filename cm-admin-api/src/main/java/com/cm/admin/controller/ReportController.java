package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.ReportAppService;
import com.cm.admin.vo.ReportAlarmVO;
import com.cm.admin.vo.ReportOnlineRecordVO;
import com.cm.admin.vo.ReportOnlineVehicleVO;
import com.cm.admin.vo.ReportOptionsVO;
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

@Tag(name = "报表与导出", description = "报警查询、上下线记录、当前在线与停车报警的查询与 Excel 导出")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private static final String PERMISSION = "hasAuthority('report:view')";

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReportAppService reportAppService;

    @Operation(summary = "报警查询分页", description = "时间范围必填，最长 366 天；结果受服务端数据范围约束")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无报表权限，或所选部门不在数据范围内")
    @PreAuthorize(PERMISSION)
    @GetMapping("/alarms")
    public ApiResult<PageResult<ReportAlarmVO>> alarms(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "报警类型编码，如 1 超速、Parking 停车")
            @RequestParam(required = false) String alarmType,
            @Parameter(description = "报警来源编码：terminal_alarm / platform_alarm / gov_alarm")
            @RequestParam(required = false) String alarmSource,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResult.ok(reportAppService.alarmPage(principal.getUserId(), page, size, plateNo, departmentId,
                alarmType, alarmSource, startTime, endTime));
    }

    @Operation(summary = "导出报警查询", description = "与分页同一筛选条件，最多 5 万行")
    @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流")
    @PreAuthorize(PERMISSION)
    @GetMapping("/alarms/export")
    public void exportAlarms(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "报警类型编码")
            @RequestParam(required = false) String alarmType,
            @Parameter(description = "报警来源编码")
            @RequestParam(required = false) String alarmSource,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "报警查询");
        reportAppService.exportAlarms(principal.getUserId(), plateNo, departmentId, alarmType, alarmSource,
                startTime, endTime, response.getOutputStream());
    }

    @Operation(summary = "上下线记录分页", description = "记录类型取 GpsOnline / GpsOffline，空表示全部")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @PreAuthorize(PERMISSION)
    @GetMapping("/online-records")
    public ApiResult<PageResult<ReportOnlineRecordVO>> onlineRecords(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "记录类型：GpsOnline / GpsOffline")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResult.ok(reportAppService.onlineRecordPage(principal.getUserId(), page, size, plateNo,
                departmentId, eventType, startTime, endTime));
    }

    @Operation(summary = "导出上下线记录", description = "与分页同一筛选条件，最多 5 万行")
    @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流")
    @PreAuthorize(PERMISSION)
    @GetMapping("/online-records/export")
    public void exportOnlineRecords(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "记录类型：GpsOnline / GpsOffline")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "车辆上下线记录");
        reportAppService.exportOnlineRecords(principal.getUserId(), plateNo, departmentId, eventType,
                startTime, endTime, response.getOutputStream());
    }

    @Operation(summary = "当前车辆在线情况分页",
            description = "在线态来自 Redis 租约，离线时长来自最新位置；status 取 online / offline，"
                    + "offlineDays 表示离线超过 N 天")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @PreAuthorize(PERMISSION)
    @GetMapping("/online-vehicles")
    public ApiResult<PageResult<ReportOnlineVehicleVO>> onlineVehicles(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "终端 SIM 前缀，空表示不过滤")
            @RequestParam(required = false) String simNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "在线状态：online / offline")
            @RequestParam(required = false) String status,
            @Parameter(description = "离线天数阈值，只对离线车辆生效")
            @RequestParam(required = false) Integer offlineDays) {
        return ApiResult.ok(reportAppService.onlineVehiclePage(principal.getUserId(), page, size, plateNo,
                simNo, departmentId, status, offlineDays));
    }

    @Operation(summary = "导出当前车辆在线情况", description = "与分页同一筛选条件")
    @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流")
    @PreAuthorize(PERMISSION)
    @GetMapping("/online-vehicles/export")
    public void exportOnlineVehicles(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "终端 SIM 前缀，空表示不过滤")
            @RequestParam(required = false) String simNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "在线状态：online / offline")
            @RequestParam(required = false) String status,
            @Parameter(description = "离线天数阈值，只对离线车辆生效")
            @RequestParam(required = false) Integer offlineDays,
            HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "当前车辆在线情况");
        reportAppService.exportOnlineVehicles(principal.getUserId(), plateNo, simNo, departmentId, status,
                offlineDays, response.getOutputStream());
    }

    @Operation(summary = "停车报警分页", description = "固定平台通道 + Parking 类型，口径对齐旧停车记录页")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @PreAuthorize(PERMISSION)
    @GetMapping("/parkings")
    public ApiResult<PageResult<ReportAlarmVO>> parkings(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "报警来源编码")
            @RequestParam(required = false) String alarmSource,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResult.ok(reportAppService.parkingPage(principal.getUserId(), page, size, plateNo,
                departmentId, alarmSource, startTime, endTime));
    }

    @Operation(summary = "导出停车报警", description = "与分页同一筛选条件，最多 5 万行")
    @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流")
    @PreAuthorize(PERMISSION)
    @GetMapping("/parkings/export")
    public void exportParkings(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "部门 ID，必须在当前账号数据范围内")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "报警来源编码")
            @RequestParam(required = false) String alarmSource,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "停车报警记录");
        reportAppService.exportParkings(principal.getUserId(), plateNo, departmentId, alarmSource,
                startTime, endTime, response.getOutputStream());
    }

    @Operation(summary = "报表筛选项", description = "报警类型、报警来源、车牌颜色、上下线类型与在线状态")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @PreAuthorize(PERMISSION)
    @GetMapping("/options")
    public ApiResult<ReportOptionsVO> options() {
        return ApiResult.ok(reportAppService.options());
    }

    private void prepareExcelResponse(HttpServletResponse response, String baseName) {
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = baseName + "_" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }
}
