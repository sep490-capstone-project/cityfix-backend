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
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {EvidenceMapper.class})
public interface TicketStepMapper {

    @Mapping(target = "stepId", source = "step.stepId")
    @Mapping(target = "stepName", source = "step.stepName")
    @Mapping(target = "assigneeId", source = "assignee.userId")
    @Mapping(target = "assigneeName", source = "assignee.fullName")
    @Mapping(target = "dependsOnStepIds", source = "dependsOn")
    TicketStepResponse toTicketStepResponse(TicketStep ticketStep);

    List<TicketStepResponse> toTicketStepResponseList(List<TicketStep> ticketSteps);

    default List<UUID> mapDependsOnToIds(java.util.Set<TicketStep> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return dependsOn.stream()
                .filter(java.util.Objects::nonNull)
                .map(TicketStep::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "step", source = "step")
    @Mapping(target = "assignee", source = "assignee")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "dependsOn", ignore = true)
    @Mapping(target = "dependentSteps", ignore = true)
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
            com.cityfix.backend.model.enums.AssignmentStatus status
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "step", source = "step")
    @Mapping(target = "assignee", source = "assignee")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "dependsOn", ignore = true)
    @Mapping(target = "dependentSteps", ignore = true)
    @Mapping(target = "startedAt", source = "startedAt")
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "deadline", source = "deadline")
    @Mapping(target = "notes", source = "notes")
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TicketStep toTicketStepWithTimeline(
            com.cityfix.backend.model.entity.Ticket ticket,
            com.cityfix.backend.model.entity.Step step,
            com.cityfix.backend.model.entity.User assignee,
            com.cityfix.backend.model.enums.AssignmentStatus status,
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime deadline,
            String notes
    );

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDeadline(UpdateTicketStepDeadlineRequest request, @MappingTarget TicketStep ticketStep);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "startedAt", source = "startedAt")
    @Mapping(target = "deadline", source = "deadline")
    @Mapping(target = "updatedAt", source = "updatedAt")
    void updateTicketStepTimeline(
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime deadline,
            java.time.LocalDateTime updatedAt,
            @MappingTarget TicketStep ticketStep
    );
}

