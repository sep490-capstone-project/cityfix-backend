package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest;
import com.cityfix.backend.model.dto.request.UpdateTicketStepOrderRequest;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.TicketStep;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketStepMapper {

    @Mapping(target = "stepId", source = "step.stepId")
    @Mapping(target = "stepName", source = "step.stepName")
    @Mapping(target = "assigneeId", source = "assignee.userId")
    @Mapping(target = "assigneeName", source = "assignee.fullName")
    TicketStepResponse toTicketStepResponse(TicketStep ticketStep);

    List<TicketStepResponse> toTicketStepResponseList(List<TicketStep> ticketSteps);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "step", source = "step")
    @Mapping(target = "assignee", source = "assignee")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "orderIndex", source = "orderIndex")
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "deadline", ignore = true)
    @Mapping(target = "notes", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TicketStep toTicketStep(
            com.cityfix.backend.model.entity.Ticket ticket,
            com.cityfix.backend.model.entity.Step step,
            com.cityfix.backend.model.entity.User assignee,
            com.cityfix.backend.model.enums.AssignmentStatus status,
            Integer orderIndex
    );

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDeadline(UpdateTicketStepDeadlineRequest request, @MappingTarget TicketStep ticketStep);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateOrderIndex(UpdateTicketStepOrderRequest request, @MappingTarget TicketStep ticketStep);
}

