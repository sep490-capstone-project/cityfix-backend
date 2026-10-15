package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Mapper(componentModel = "spring", uses = {TicketStepMapper.class, SubCategoryMapper.class})
public interface TicketMapper {

    DuplicateTicketResponse toDuplicateTicketResponse(DuplicateTicketProjection projection);

    List<DuplicateTicketResponse> toDuplicateTicketResponseList(List<DuplicateTicketProjection> projections);

    @Mapping(target = "id", source = "ticketId")
    @Mapping(target = "createdBy", source = "user.userId")
    @Mapping(target = "creatorFullName", source = "user.fullName")
    @Mapping(target = "creatorPhoneNumber", source = "user.phoneNumber")
    @Mapping(target = "categoryId", source = "category.categoryId")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "latitude", expression = "java(ticket.getLocation() != null ? ticket.getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(ticket.getLocation() != null ? ticket.getLocation().getX() : null)")
    @Mapping(target = "imageUrl", expression = "java(ticket.getImages() != null && !ticket.getImages().isEmpty() ? ticket.getImages().get(0).getImageUrl() : null)")
    @Mapping(target = "images", expression = "java(ticket.getImages() != null ? ticket.getImages().stream().map(com.cityfix.backend.model.entity.ReportImage::getImageUrl).toList() : java.util.Collections.emptyList())")
    @Mapping(target = "subCategories", source = "subCategories")
    @Mapping(target = "departmentId", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    TicketResponse toTicketResponse(Ticket ticket);

    @AfterMapping
    default void hideUserIfAnonymous(Ticket ticket, @MappingTarget TicketResponse response) {
        if (ticket == null || response == null) return;
        if (Boolean.TRUE.equals(ticket.getIsAnonymous())) {
            boolean isAdmin = false;
            try {
                org.springframework.security.core.Authentication auth =
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                    boolean hasAdminAuthority = auth.getAuthorities() != null && auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));

                    boolean hasAdminPrincipal = false;
                    Object principal = auth.getPrincipal();
                    if (principal instanceof com.cityfix.backend.model.entity.User u) {
                        hasAdminPrincipal = u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleName());
                    }

                    isAdmin = hasAdminAuthority || hasAdminPrincipal;
                }
            } catch (Exception ignored) {
                isAdmin = false;
            }

            if (!isAdmin) {
                response.setCreatedBy(null);
                response.setCreatorFullName(null);
                response.setCreatorPhoneNumber(null);
            }
        }
    }

    @AfterMapping
    default void mapDepartmentFromSteps(Ticket ticket, @MappingTarget TicketResponse response) {
        if (ticket == null || response == null) return;
        if (ticket.getSteps() != null && !ticket.getSteps().isEmpty()) {
            List<TicketStep> validSteps = ticket.getSteps().stream()
                    .filter(s -> !s.isDeleted() && s.getAssignee() != null && s.getAssignee().getDepartment() != null)
                    .toList();

            if (!validSteps.isEmpty()) {
                // Ưu tiên IN_PROGRESS
                Optional<TicketStep> inProgress = validSteps.stream()
                        .filter(s -> s.getStatus() == AssignmentStatus.IN_PROGRESS)
                        .findFirst();
                if (inProgress.isPresent()) {
                    Department dept = inProgress.get().getAssignee().getDepartment();
                    response.setDepartmentId(dept.getDepartmentId());
                    response.setDepartmentName(dept.getName());
                    return;
                }

                // Ưu tiên ASSIGNED
                Optional<TicketStep> assigned = validSteps.stream()
                        .filter(s -> s.getStatus() == AssignmentStatus.ASSIGNED)
                        .findFirst();
                if (assigned.isPresent()) {
                    Department dept = assigned.get().getAssignee().getDepartment();
                    response.setDepartmentId(dept.getDepartmentId());
                    response.setDepartmentName(dept.getName());
                    return;
                }

                // Ưu tiên step chưa bị CANCELLED theo orderIndex nhỏ nhất
                Optional<TicketStep> notCancelled = validSteps.stream()
                        .filter(s -> s.getStatus() != AssignmentStatus.CANCELLED)
                        .min(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())));
                if (notCancelled.isPresent()) {
                    Department dept = notCancelled.get().getAssignee().getDepartment();
                    response.setDepartmentId(dept.getDepartmentId());
                    response.setDepartmentName(dept.getName());
                    return;
                }

                Department dept = validSteps.get(0).getAssignee().getDepartment();
                response.setDepartmentId(dept.getDepartmentId());
                response.setDepartmentName(dept.getName());
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
    @Mapping(target = "ratings", ignore = true)
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
