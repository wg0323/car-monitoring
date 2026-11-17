package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.AlarmAppService;
import com.cm.admin.vo.AlarmBatchProcessRequest;
import com.cm.admin.vo.AlarmNotificationVO;
import com.cm.admin.vo.AlarmProcessRequest;
import com.cm.admin.vo.AlarmProcessResultVO;
import com.cm.admin.vo.AlarmRecordVO;
import com.cm.domain.exception.CmBusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "报警处理", description = "报警明细、处理动作与右下角报警弹窗；数据范围与报表、实时监控同口径")
@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private static final String DEFAULT_PAGE_SIZE = "10";

    private static final String DEFAULT_LOOKBACK_MINUTES = "5";

    private final AlarmAppService alarmAppService;

    @Operation(summary = "报警明细分页",
            description = "报警处理页与底部报警数据共用；时间窗必填，车牌按前缀匹配，结果受服务端数据范围约束")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "400", description = "时间窗缺失或倒置")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无报警查看权限")
    @PreAuthorize("hasAuthority('alarm:view')")
    @GetMapping
    public ApiResult<PageResult<AlarmRecordVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "处理状态：true 已处理，false 未处理，不传为全部")
            @RequestParam(required = false) Boolean processed,
            @Parameter(description = "车牌前缀，空表示不限", example = "甘A")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResult.ok(alarmAppService.page(principal.getUserId(), page, size, processed,
                plateNo, startTime, endTime));
    }

    @Operation(summary = "增量报警轮询",
            description = "返回 sinceId 之后、近 minutes 分钟内开始的报警，按主键升序；"
                    + "页面首次调用传 sinceId=0 只取基线，之后用返回的最大 id 续查")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无报警查看权限")
    @PreAuthorize("hasAuthority('alarm:view')")
    @GetMapping("/notifications")
    public ApiResult<List<AlarmNotificationVO>> notifications(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "上次已处理的最大报警 ID，0 表示取基线", example = "0")
            @RequestParam(defaultValue = "0") long sinceId,
            @Parameter(description = "回看分钟数，1~60", example = DEFAULT_LOOKBACK_MINUTES)
            @RequestParam(defaultValue = DEFAULT_LOOKBACK_MINUTES) int minutes) {
        return ApiResult.ok(alarmAppService.notifications(principal.getUserId(), sinceId, minutes));
    }

    @Operation(summary = "单条报警处理",
            description = "标记已处理并写处理人、处理时间与备注；已处理、越界或不存在的记录返回 400")
    @ApiResponse(responseCode = "200", description = "处理成功")
    @ApiResponse(responseCode = "400", description = "报警不存在、已处理或不在数据范围内")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无报警处理权限")
    @PreAuthorize("hasAuthority('alarm:process')")
    @PostMapping("/{id}/process")
    public ApiResult<Void> process(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "报警记录 ID，字符串形式", required = true) @PathVariable String id,
            @RequestBody(required = false) AlarmProcessRequest request) {
        alarmAppService.process(principal.getUserId(), principal.getDisplayName(), parseAlarmId(id),
                request == null ? null : request.getRemark());
        return ApiResult.ok(null);
    }

    @Operation(summary = "批量报警处理",
            description = "逐条独立处理；已处理、越界或不存在的记录进 skipped 并带原因，其余照常处理")
    @ApiResponse(responseCode = "200", description = "处理完成，返回成功条数与跳过明细")
    @ApiResponse(responseCode = "400", description = "ID 为空、格式不合法或超过 100 条")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无报警处理权限")
    @PreAuthorize("hasAuthority('alarm:process')")
    @PostMapping("/batch-process")
    public ApiResult<AlarmProcessResultVO> batchProcess(
            @AuthenticationPrincipal CmUserDetails principal,
            @Valid @RequestBody AlarmBatchProcessRequest request) {
        List<Long> ids = request.getIds().stream().map(this::parseAlarmId).toList();
        return ApiResult.ok(alarmAppService.processBatch(principal.getUserId(),
                principal.getDisplayName(), ids, request.getRemark()));
    }

    /** 雪花 ID 走字符串传输，格式错误按业务异常返回 400，而不是抛 NumberFormatException 变成 500。 */
    private Long parseAlarmId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CmBusinessException("报警 ID 不能为空");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new CmBusinessException("报警 ID 格式不正确：" + raw);
        }
    }
}
