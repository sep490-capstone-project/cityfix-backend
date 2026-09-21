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
}
