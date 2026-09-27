package com.cityfix.backend.mapper;

import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketAssignment;
import com.cityfix.backend.model.enums.AssignmentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketAssignmentMapper {

    @Mapping(target = "assignmentId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "status", source = "status")
    TicketAssignment toTicketAssignment(Ticket ticket, Department department, AssignmentStatus status);
}
