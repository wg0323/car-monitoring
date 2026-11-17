package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.common.PageResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.GovCommandAppService;
import com.cm.admin.vo.GovCommandVO;
import com.cm.admin.vo.LinkCommandRequest;
import com.cm.admin.vo.LinkStateVO;
import com.cm.domain.command.GovCommandQuery;
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

@Tag(name = "809 监管平台命令", description = "链路命令下发、主从链路状态与上级下发报文；数据直接读 cm_jt809_command")
@RestController
@RequestMapping("/api/v1/gov809")
@RequiredArgsConstructor
public class GovCommandController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final GovCommandAppService govCommandAppService;

    @Operation(summary = "分页查询 809 指令与报文",
            description = "source 区分本端下发与上级下发；车牌只做前缀匹配，时间窗可选。"
                    + "809 报文是平台级审计数据（旧系统同样的 paginate 未做部门隔离），不按车辆数据范围过滤")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无终端指令权限")
    @PreAuthorize("hasAuthority('command:view')")
    @GetMapping("/commands")
    public ApiResult<PageResult<GovCommandVO>> page(
            @AuthenticationPrincipal CmUserDetails principal,
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 1000", example = DEFAULT_PAGE_SIZE)
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) long size,
            @Parameter(description = "记录来源：SEND-本端下发，RECEIVE-上级下发，空表示全部")
            @RequestParam(required = false) String source,
            @Parameter(description = "车牌号前缀，空表示不过滤")
            @RequestParam(required = false) String plateNo,
            @Parameter(description = "状态：待下发 / 执行中 / 发送成功 / 发送失败 / 接收成功 / 重复接收 / 失败")
            @RequestParam(required = false) String status,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss，空表示不限")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss，空表示不限")
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        GovCommandQuery query = new GovCommandQuery(source, plateNo, status, startTime, endTime);
        return ApiResult.ok(govCommandAppService.page(page, size, query));
    }

    @Operation(summary = "下发 809 链路命令",
            description = "只接受 0x1001 连接主链路、0x1003 主链路注销、0x1007 主链路断开通知、"
                    + "0x1008 主动关闭主从链路通知；写入待下发队列后由企业侧链路进程执行并回写结果")
    @ApiResponse(responseCode = "200", description = "受理成功，返回记录 ID")
    @ApiResponse(responseCode = "400", description = "链路命令不在允许范围内")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无 809 链路命令权限")
    @PreAuthorize("hasAuthority('command:gov809:link')")
    @PostMapping("/link-commands")
    public ApiResult<Long> sendLinkCommand(
            @AuthenticationPrincipal CmUserDetails principal,
            @Valid @RequestBody LinkCommandRequest request) {
        return ApiResult.ok(govCommandAppService.sendLinkCommand(principal.getUserId(), request.getLinkCmd()));
    }

    @Operation(summary = "查询主从链路状态", description = "读 cm_gov809_link 的企业侧快照，含待补发条数与最后错误")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无终端指令权限")
    @PreAuthorize("hasAuthority('command:view')")
    @GetMapping("/link-state")
    public ApiResult<LinkStateVO> linkState(@AuthenticationPrincipal CmUserDetails principal) {
        return ApiResult.ok(govCommandAppService.linkState());
    }
}
