package com.cm.infrastructure.converter;

import com.cm.infrastructure.dto.CommandAcceptCommand;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.entity.CmVehicle;

/**
 * 指令受理的服务端对象组装。
 * 目标对象的字段来自车辆资料、服务端生成值与常量，源 DTO 只提供业务参数，
 * 属于非等价映射，无法用 BeanUtils 一次完成，因此集中在本 converter 里逐字段赋值。
 */
public final class CommandConverter {

    /** 指令初始状态，对应方案 12.2 的「已受理」。 */
    public static final String STATUS_ACCEPTED = "ACCEPTED";

    /** Outbox 初始状态，等待 P3 的 relay 抢占。 */
    public static final String STATUS_PENDING = "PENDING";

    /** P3 之前的默认 Exchange，不允许由调用方指定。 */
    public static final String DEFAULT_EXCHANGE = "cm.command";

    /** P3 之前的默认 routingKey，不允许由调用方指定。 */
    public static final String DEFAULT_ROUTING_KEY = "command.downlink";

    private static final int FIRST_ATTEMPT_NO = 1;

    private static final int INITIAL_RETRY_COUNT = 0;

    private static final int INITIAL_TERMINAL_SERIAL_NO = 0;

    private CommandConverter() {
    }

    public static CmTerminalCommand toEntity(String commandId, CmVehicle vehicle,
                                             CommandAcceptCommand command, Long operatorId) {
        CmTerminalCommand entity = new CmTerminalCommand();
        entity.setCommandId(commandId);
        entity.setCompanyId(vehicle.getCompanyId());
        entity.setDepartmentId(vehicle.getDepartmentId());
        entity.setVehicleId(vehicle.getId());
        entity.setPlateNo(vehicle.getPlateNo());
        entity.setSimNo(vehicle.getSimNo());
        entity.setCommandType(command.getCommandType());
        entity.setCommandCode(command.getCommandCode());
        entity.setCommandData(command.getCommandData());
        entity.setTerminalSerialNo(INITIAL_TERMINAL_SERIAL_NO);
        entity.setAttemptNo(FIRST_ATTEMPT_NO);
        entity.setCommandStatus(STATUS_ACCEPTED);
        entity.setOperatorId(operatorId);
        return entity;
    }

    /**
     * P2 的 payload 只存 commandId 引用；P3 的 relay 按它组装方案 11.2 的版本化 JSON 事件，
     * 不在这里拼接自由格式消息体。
     */
    public static CmCommandOutbox toOutbox(String commandId) {
        CmCommandOutbox outbox = new CmCommandOutbox();
        outbox.setCommandId(commandId);
        outbox.setExchangeName(DEFAULT_EXCHANGE);
        outbox.setRoutingKey(DEFAULT_ROUTING_KEY);
        outbox.setPayload(commandId);
        outbox.setPublishStatus(STATUS_PENDING);
        outbox.setRetryCount(INITIAL_RETRY_COUNT);
        return outbox;
    }
}
