package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;
import com.cityfix.backend.model.entity.TicketSubCategory;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {TicketStepMapper.class, SubCategoryMapper.class})
public interface TicketMapper {

    DuplicateTicketResponse toDuplicateTicketResponse(DuplicateTicketProjection projection);

    List<DuplicateTicketResponse> toDuplicateTicketResponseList(List<DuplicateTicketProjection> projections);

    @Mapping(target = "id", source = "ticketId")
    @Mapping(target = "createdBy", source = "user.userId")
    @Mapping(target = "categoryId", source = "category.categoryId")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "latitude", expression = "java(ticket.getLocation() != null ? ticket.getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(ticket.getLocation() != null ? ticket.getLocation().getX() : null)")
    @Mapping(target = "imageUrl", expression = "java(ticket.getImages() != null && !ticket.getImages().isEmpty() ? ticket.getImages().get(0).getImageUrl() : null)")
    @Mapping(target = "subCategories", expression = "java(mapTicketSubCategories(ticket.getSubCategories(), subCategoryMapper))")
    @Mapping(target = "steps", source = "steps")
    TicketResponse toTicketResponse(Ticket ticket, @Context SubCategoryMapper subCategoryMapper);

    default TicketResponse toTicketResponse(Ticket ticket) {
        return toTicketResponse(ticket, null);
    }

    default List<SubCategoryResponse> mapTicketSubCategories(List<TicketSubCategory> ticketSubCategories, SubCategoryMapper subCategoryMapper) {
        if (ticketSubCategories == null || ticketSubCategories.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return ticketSubCategories.stream()
                .filter(tsc -> tsc.getSubCategory() != null)
                .map(tsc -> subCategoryMapper != null ? subCategoryMapper.toResponse(tsc.getSubCategory()) : 
                        SubCategoryResponse.builder()
                                .subCategoryId(tsc.getSubCategory().getSubCategoryId())
                                .categoryId(tsc.getSubCategory().getCategory() != null ? tsc.getSubCategory().getCategory().getCategoryId() : null)
                                .name(tsc.getSubCategory().getName())
                                .description(tsc.getSubCategory().getDescription())
                                .sla(tsc.getSubCategory().getSla())
                                .priority(tsc.getSubCategory().getPriority())
                                .isActive(tsc.getSubCategory().isActive())
                                .build())
                .collect(Collectors.toList());
    }

    @AfterMapping
    default void hideUserIfAnonymous(Ticket ticket, @MappingTarget TicketResponse response) {
        if (ticket == null || response == null) return;
        if (Boolean.TRUE.equals(ticket.getIsAnonymous())) {
            boolean isAdmin = false;
            try {
                org.springframework.security.core.Authentication auth =
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    isAdmin = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
                }
            } catch (Exception ignored) {}

            if (!isAdmin) {
                response.setCreatedBy(null);
            }
        }
    }

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
    @Mapping(target = "priority", source = "request.priority")
    @Mapping(target = "locationMethod", source = "request.locationMethod")
    @Mapping(target = "address", source = "request.address")
    @Mapping(target = "subCategories", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    @Mapping(target = "isAnonymous", expression = "java(request.getIsAnonymous() != null ? request.getIsAnonymous() : false)")
    Ticket toTicket(
            com.cityfix.backend.model.dto.request.CreateTicketRequest request,
            com.cityfix.backend.model.entity.Category category,
            com.cityfix.backend.model.entity.User user,
            org.locationtech.jts.geom.Point location,
            com.cityfix.backend.model.enums.TicketStatus status,
            boolean needsManualReview
    );
}

