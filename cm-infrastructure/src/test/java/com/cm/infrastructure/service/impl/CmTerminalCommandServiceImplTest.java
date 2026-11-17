package com.cm.infrastructure.service.impl;

import com.cm.domain.exception.CmBusinessException;
import com.cm.domain.scope.DataScope;
import com.cm.infrastructure.converter.CommandConverter;
import com.cm.infrastructure.dto.CommandAcceptCommand;
import com.cm.infrastructure.persistence.entity.CmCommandOutbox;
import com.cm.infrastructure.persistence.entity.CmTerminalCommand;
import com.cm.infrastructure.persistence.entity.CmVehicle;
import com.cm.infrastructure.persistence.mapper.CmCommandOutboxMapper;
import com.cm.infrastructure.persistence.mapper.CmTerminalCommandMapper;
import com.cm.infrastructure.persistence.mapper.CmVehicleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmTerminalCommandServiceImplTest {

    private static final DataScope ADMIN_SCOPE = DataScope.superAdmin(1L);

    private static final DataScope LIMITED_SCOPE = new DataScope(1L, false, List.of(9L), List.of(99L));

    @Mock
    private CmTerminalCommandMapper terminalCommandMapper;

    @Mock
    private CmCommandOutboxMapper commandOutboxMapper;

    @Mock
    private CmVehicleMapper vehicleMapper;

    private CmTerminalCommandServiceImpl commandService;

    @BeforeEach
    void setUp() {
        commandService = new CmTerminalCommandServiceImpl(commandOutboxMapper, vehicleMapper);
        ReflectionTestUtils.setField(commandService, "baseMapper", terminalCommandMapper);
    }

    @Test
    void should_reject_when_vehicle_id_is_zero_or_negative() {
        CommandAcceptCommand zero = validCommand();
        zero.setVehicleId(0L);
        CommandAcceptCommand negative = validCommand();
        negative.setVehicleId(-1L);

        assertThrows(CmBusinessException.class, () -> commandService.accept(ADMIN_SCOPE, zero));
        assertThrows(CmBusinessException.class, () -> commandService.accept(ADMIN_SCOPE, negative));

        verify(terminalCommandMapper, never()).insert(any(CmTerminalCommand.class));
    }

    @Test
    void should_reject_when_vehicle_id_is_null() {
        CommandAcceptCommand command = validCommand();
        command.setVehicleId(null);

        assertThrows(CmBusinessException.class, () -> commandService.accept(ADMIN_SCOPE, command));

        verify(commandOutboxMapper, never()).insert(any(CmCommandOutbox.class));
    }

    @Test
    void should_reject_when_command_code_is_blank_or_too_long() {
        CommandAcceptCommand blank = validCommand();
        blank.setCommandCode("  ");
        CommandAcceptCommand tooLong = validCommand();
        tooLong.setCommandCode("X".repeat(65));

        assertThrows(CmBusinessException.class, () -> commandService.accept(ADMIN_SCOPE, blank));
        assertThrows(CmBusinessException.class, () -> commandService.accept(ADMIN_SCOPE, tooLong));

        verify(terminalCommandMapper, never()).insert(any(CmTerminalCommand.class));
    }

    @Test
    void should_reject_when_scope_is_missing() {
        assertThrows(CmBusinessException.class, () -> commandService.accept(null, validCommand()));

        verify(vehicleMapper, never()).selectById(any());
    }

    @Test
    void should_reject_when_vehicle_does_not_exist() {
        when(vehicleMapper.selectById(42L)).thenReturn(null);

        assertThrows(CmBusinessException.class, () -> commandService.accept(ADMIN_SCOPE, validCommand()));

        verify(terminalCommandMapper, never()).insert(any(CmTerminalCommand.class));
    }

    @Test
    void should_reject_when_vehicle_is_out_of_data_scope() {
        when(vehicleMapper.selectById(42L)).thenReturn(vehicle());

        assertThrows(CmBusinessException.class, () -> commandService.accept(LIMITED_SCOPE, validCommand()));

        verify(terminalCommandMapper, never()).insert(any(CmTerminalCommand.class));
    }

    @Test
    void should_write_server_generated_fields_when_input_is_valid() {
        when(vehicleMapper.selectById(42L)).thenReturn(vehicle());
        when(terminalCommandMapper.insert(any(CmTerminalCommand.class))).thenReturn(1);
        when(commandOutboxMapper.insert(any(CmCommandOutbox.class))).thenReturn(1);
        ArgumentCaptor<CmTerminalCommand> commandCaptor = ArgumentCaptor.forClass(CmTerminalCommand.class);
        ArgumentCaptor<CmCommandOutbox> outboxCaptor = ArgumentCaptor.forClass(CmCommandOutbox.class);

        String commandId = commandService.accept(ADMIN_SCOPE, validCommand());

        assertFalse(commandId.isBlank());
        verify(terminalCommandMapper).insert(commandCaptor.capture());
        verify(commandOutboxMapper).insert(outboxCaptor.capture());
        CmTerminalCommand saved = commandCaptor.getValue();
        assertEquals(commandId, saved.getCommandId());
        assertEquals(Long.valueOf(9L), saved.getCompanyId());
        assertEquals(Long.valueOf(3L), saved.getDepartmentId());
        assertEquals("京A12345", saved.getPlateNo());
        assertEquals(Long.valueOf(1L), saved.getOperatorId());
        assertEquals(CommandConverter.STATUS_ACCEPTED, saved.getCommandStatus());
        assertEquals(Integer.valueOf(1), saved.getAttemptNo());
        CmCommandOutbox outbox = outboxCaptor.getValue();
        assertEquals(commandId, outbox.getCommandId());
        assertEquals(CommandConverter.DEFAULT_EXCHANGE, outbox.getExchangeName());
        assertEquals(CommandConverter.DEFAULT_ROUTING_KEY, outbox.getRoutingKey());
        assertEquals(CommandConverter.STATUS_PENDING, outbox.getPublishStatus());
    }

    private CommandAcceptCommand validCommand() {
        CommandAcceptCommand command = new CommandAcceptCommand();
        command.setVehicleId(42L);
        command.setCommandType(1);
        command.setCommandCode("0x8201");
        command.setCommandData("");
        return command;
    }

    private CmVehicle vehicle() {
        CmVehicle vehicle = new CmVehicle();
        vehicle.setId(42L);
        vehicle.setCompanyId(9L);
        vehicle.setDepartmentId(3L);
        vehicle.setPlateNo("京A12345");
        vehicle.setSimNo("13912345678");
        vehicle.setDeleted(false);
        return vehicle;
    }
}
