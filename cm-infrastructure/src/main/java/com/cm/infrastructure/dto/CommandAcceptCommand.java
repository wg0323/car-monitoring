package com.cm.infrastructure.dto;

import lombok.Data;

/**
 * 指令受理入参，只放业务参数。
 * companyId / departmentId / plateNo / simNo / operatorId / commandId 以及 MQ 路由信息
 * 全部由服务端从车辆资料与认证主体生成，客户端不得自带，避免伪造权限字段。
 */
@Data
public class CommandAcceptCommand {

    private Long vehicleId;

    private Integer commandType;

    private String commandCode;

    private String commandData;
}
