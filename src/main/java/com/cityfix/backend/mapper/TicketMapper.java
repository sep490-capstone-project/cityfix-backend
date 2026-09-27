package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    DuplicateTicketResponse toDuplicateTicketResponse(DuplicateTicketProjection projection);

    List<DuplicateTicketResponse> toDuplicateTicketResponseList(List<DuplicateTicketProjection> projections);

    @Mapping(target = "id", source = "ticketId")
    @Mapping(target = "createdBy", source = "user.userId")
    @Mapping(target = "categoryId", source = "category.categoryId")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "latitude", expression = "java(ticket.getLocation() != null ? ticket.getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(ticket.getLocation() != null ? ticket.getLocation().getX() : null)")
    @Mapping(target = "imageUrl", ignore = true)
    TicketResponse toTicketResponse(Ticket ticket);

    @Mapping(target = "departmentId", source = "department.departmentId")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "assigneeId", source = "assignee.userId")
    @Mapping(target = "assigneeName", source = "assignee.fullName")
    com.cityfix.backend.model.dto.response.TicketAssignmentResponse toAssignmentResponse(com.cityfix.backend.model.entity.TicketAssignment assignment);

    @Mapping(target = "ticketId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "category", source = "category")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "needsManualReview", source = "needsManualReview")
    @Mapping(target = "isLifeThreatening", constant = "false")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "severity", source = "request.severity")
    @Mapping(target = "dangerLevel", source = "request.dangerLevel")
    @Mapping(target = "locationMethod", source = "request.locationMethod")
    @Mapping(target = "address", source = "request.address")
    Ticket toTicket(
            com.cityfix.backend.model.dto.request.CreateTicketRequest request,
            com.cityfix.backend.model.entity.Category category,
            com.cityfix.backend.model.entity.User user,
            org.locationtech.jts.geom.Point location,
            com.cityfix.backend.model.enums.TicketStatus status,
            boolean needsManualReview
    );
}
