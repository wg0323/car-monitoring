package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.CommandAppService;
import com.cm.admin.vo.CommandSendRequest;
import com.cm.admin.vo.CommandVehicleVO;
import com.cm.admin.vo.CommandVO;
import com.cm.domain.command.CommandQuery;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "终端指令", description = "点名下发与终端命令结果回填；结果受服务端数据范围约束")
@RestController
@RequestMapping("/api/v1/commands")
@RequiredArgsConstructor
public class CommandController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final CommandAppService commandAppService;

    @Operation(summary = "下发点名",
            description = "0x8201 位置信息查询。车辆不在当前账号数据范围返回 403，"
                    + "终端不在线返回 409；受理后命令与 Outbox 同事务落库，结果在分页接口回填")
    @ApiResponse(responseCode = "200", description = "受理成功，返回 commandId")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无点名权限，或车辆不在数据范围内")
    @ApiResponse(responseCode = "409", description = "车辆不在线，无法下发即时指令")
    @PreAuthorize("hasAuthority('command:send')")
    @PostMapping("/position-query")
    public ApiResult<String> positionQuery(
            @AuthenticationPrincipal CmUserDetails principal,
            @Valid @RequestBody CommandSendRequest request) {
        return ApiResult.ok(commandAppService.sendPositionQuery(principal.getUserId(), request.getVehicleId()));
    }

    @Operation(summary = "分页查询命令结果",
            description = "total 为按当前账号数据范围过滤后的总行数；车牌只做前缀匹配，时间窗可选")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无终端指令权限")
    @PreAuthorize("hasAuthority('command:view')")
    @GetMapping
    public ApiResult<PageResult<CommandVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "命令状态原值：ACCEPTED / SENT_MQ / SENT_TERMINAL / ACKED / SUCCESS / FAILED / TIMEOUT")
            @RequestParam(required = false) String status,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss，空表示不限")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss，空表示不限")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        CommandQuery query = new CommandQuery(plateNo, status, startTime, endTime);
        return ApiResult.ok(commandAppService.page(principal.getUserId(), page, size, query));
    }

    @Operation(summary = "查询可下达命令的车辆候选",
            description = "按车牌前缀搜索，最多 20 条，带回在线态；离线车辆不允许下发即时指令")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无终端指令权限")
    @PreAuthorize("hasAuthority('command:view')")
    @GetMapping("/candidates")
    public ApiResult<List<CommandVehicleVO>> candidates(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "车牌号前缀，空表示取前 20 条")
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(commandAppService.candidates(principal.getUserId(), keyword));
    }
}
