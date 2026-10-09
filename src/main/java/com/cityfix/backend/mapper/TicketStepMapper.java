package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.TicketStep;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketStepMapper {

    @Mapping(target = "stepId", source = "step.stepId")
    @Mapping(target = "stepName", source = "step.stepName")
    @Mapping(target = "assigneeId", source = "assignee.userId")
    @Mapping(target = "assigneeName", source = "assignee.fullName")
    TicketStepResponse toTicketStepResponse(TicketStep ticketStep);

    List<TicketStepResponse> toTicketStepResponseList(List<TicketStep> ticketSteps);
}
