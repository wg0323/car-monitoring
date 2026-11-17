package com.cm.admin.controller;

import com.cm.admin.common.ApiResult;
import com.cm.admin.security.CmUserDetails;
import com.cm.admin.service.MessageAppService;
import com.cm.admin.vo.MessageBroadcastRequest;
import com.cm.admin.vo.MessageOnlineUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "消息中心", description = "在线用户列表与系统广播；点对点聊天走 /ws/message 的 WebSocket 帧")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageAppService messageAppService;

    @Operation(summary = "在线用户列表",
            description = "来自 Redis 在线集合，按用户去重；同一账号多标签页只出现一次，超过上限截断")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无消息中心权限")
    @PreAuthorize("hasAuthority('message:view')")
    @GetMapping("/online-users")
    public ApiResult<List<MessageOnlineUserVO>> onlineUsers() {
        return ApiResult.ok(messageAppService.onlineUsers());
    }

    @Operation(summary = "发布系统广播",
            description = "全平台在线账号立即收到；广播不落库，离线账号不会补发")
    @ApiResponse(responseCode = "200", description = "发布成功")
    @ApiResponse(responseCode = "400", description = "广播内容为空或超过 500 字")
    @ApiResponse(responseCode = "401", description = "未登录或会话已过期")
    @ApiResponse(responseCode = "403", description = "无系统广播权限")
    @PreAuthorize("hasAuthority('message:broadcast')")
    @PostMapping("/broadcast")
    public ApiResult<Void> broadcast(@AuthenticationPrincipal CmUserDetails principal,
                                     @Valid @RequestBody MessageBroadcastRequest request) {
        messageAppService.broadcast(principal.getUserId(), request.getText());
        return ApiResult.ok(null);
    }
}
