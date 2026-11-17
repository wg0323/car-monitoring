package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.UrgeTodoAppService;
import com.cm.admin.vo.UrgeTodoAckRequest;
import com.cm.admin.vo.UrgeTodoVO;
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

@Tag(name = "809 报警督办", description = "上级平台下发的督办请求、应答状态与 0x1401 应答下发")
@RestController
@RequestMapping("/api/v1/gov809/urge-todos")
@RequiredArgsConstructor
public class UrgeTodoController {

    private static final String DEFAULT_PAGE_SIZE = "10";

    private static final String DEFAULT_LOOKBACK_MINUTES = "60";

    private final UrgeTodoAppService urgeTodoAppService;

    @Operation(summary = "督办分页", description = "按车牌前缀、应答状态与接收时间窗查询；平台级审计数据，不按车辆部门过滤")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无终端指令权限")
    @PreAuthorize("hasAuthority('command:view')")
    @GetMapping
    public ApiResult<PageResult<UrgeTodoVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "是否已应答：true-已应答，false-未应答，空表示全部")
            @RequestParam(required = false) Boolean acknowledged,
            @Parameter(description = "接收开始时间，格式 yyyy-MM-dd HH:mm:ss，空表示不限")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "接收结束时间，格式 yyyy-MM-dd HH:mm:ss，空表示不限")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ApiResult.ok(urgeTodoAppService.page(page, size, plateNo, acknowledged, startTime, endTime));
    }

    @Operation(summary = "增量督办轮询",
            description = "返回 sinceId 之后、近 minutes 分钟内收到的未应答督办，按主键升序；"
                    + "页面首次调用传 sinceId=0 只取基线，之后用返回的最大 id 续查")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无终端指令权限")
    @PreAuthorize("hasAuthority('command:view')")
    @GetMapping("/notifications")
    public ApiResult<List<UrgeTodoVO>> notifications(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "上次已处理的最大记录 ID，0 表示取基线", example = "0")
            @RequestParam(defaultValue = "0") long sinceId,
            @Parameter(description = "回看分钟数，1~1440", example = DEFAULT_LOOKBACK_MINUTES)
            @RequestParam(defaultValue = DEFAULT_LOOKBACK_MINUTES) int minutes) {
        return ApiResult.ok(urgeTodoAppService.notifications(sinceId, minutes));
    }

    @Operation(summary = "应答督办",
            description = "只受理不直发：写入待下发指令（0x1400/0x1401），由企业侧链路进程发送并回写结果；"
                    + "已应答的督办不能重复应答")
    @ApiResponse(responseCode = "200", description = "受理成功，返回指令记录 ID")
    @ApiResponse(responseCode = "400", description = "督办不存在、已应答或结果码不合法")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无督办应答权限")
    @PreAuthorize("hasAuthority('command:gov809:urge')")
    @PostMapping("/{id}/ack")
    public ApiResult<Long> acknowledge(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "督办记录 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UrgeTodoAckRequest request) {
        return ApiResult.ok(urgeTodoAppService.acknowledge(principal.getUserId(), id, request.getResult()));
    }
}
