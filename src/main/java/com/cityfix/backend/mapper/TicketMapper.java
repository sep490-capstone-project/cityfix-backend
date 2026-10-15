package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(componentModel = "spring", uses = {TicketStepMapper.class, SubCategoryMapper.class})
public abstract class TicketMapper {

    @Autowired
    protected RoutingRuleRepository routingRuleRepository;

    public abstract DuplicateTicketResponse toDuplicateTicketResponse(DuplicateTicketProjection projection);

    public abstract List<DuplicateTicketResponse> toDuplicateTicketResponseList(List<DuplicateTicketProjection> projections);

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
    public abstract TicketResponse toTicketResponse(Ticket ticket);

    @AfterMapping
    protected void hideUserIfAnonymous(Ticket ticket, @MappingTarget TicketResponse response) {
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
    protected void mapDepartment(Ticket ticket, @MappingTarget TicketResponse response) {
        if (ticket == null || response == null) return;
        Department dept = resolveDepartment(ticket);
        if (dept != null) {
            response.setDepartmentId(dept.getDepartmentId());
            response.setDepartmentName(dept.getName());
        }
    }

    private Department resolveDepartment(Ticket ticket) {
        // Ưu tiên 1: Lấy từ TicketStep đang active/được gán (ts.assignee.department)
        if (ticket.getSteps() != null && !ticket.getSteps().isEmpty()) {
            List<TicketStep> validSteps = ticket.getSteps().stream()
                    .filter(s -> !s.isDeleted() && s.getAssignee() != null && s.getAssignee().getDepartment() != null)
                    .toList();

            if (!validSteps.isEmpty()) {
                // 1.1 Ưu tiên bước đang thực hiện (IN_PROGRESS)
                Optional<TicketStep> inProgress = validSteps.stream()
                        .filter(s -> s.getStatus() == AssignmentStatus.IN_PROGRESS)
                        .findFirst();
                if (inProgress.isPresent()) {
                    return inProgress.get().getAssignee().getDepartment();
                }

                // 1.2 Ưu tiên bước đã gán (ASSIGNED)
                Optional<TicketStep> assigned = validSteps.stream()
                        .filter(s -> s.getStatus() == AssignmentStatus.ASSIGNED)
                        .findFirst();
                if (assigned.isPresent()) {
                    return assigned.get().getAssignee().getDepartment();
                }

                // 1.3 Lấy bước chưa bị hủy có orderIndex nhỏ nhất
                Optional<TicketStep> notCancelled = validSteps.stream()
                        .filter(s -> s.getStatus() != AssignmentStatus.CANCELLED)
                        .min(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())));
                if (notCancelled.isPresent()) {
                    return notCancelled.get().getAssignee().getDepartment();
                }

                return validSteps.get(0).getAssignee().getDepartment();
            }
        }

        // Ưu tiên 2: Nếu chưa gán step nào, lấy từ RoutingRule khớp Category (+ Địa chỉ vé)
        if (routingRuleRepository != null && ticket.getCategory() != null && ticket.getCategory().getCategoryId() != null) {
            List<RoutingRule> rules = routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(ticket.getCategory().getCategoryId());
            if (rules != null && !rules.isEmpty()) {
                String address = ticket.getAddress() != null ? ticket.getAddress().toLowerCase() : "";
                return rules.stream()
                        .filter(rule -> {
                            boolean provinceMatch = rule.getProvince() == null || rule.getProvince().isBlank()
                                    || address.contains(rule.getProvince().toLowerCase());
                            boolean wardMatch = rule.getWard() == null || rule.getWard().isBlank()
                                    || address.contains(rule.getWard().toLowerCase());
                            return provinceMatch && wardMatch;
                        })
                        .map(RoutingRule::getDepartment)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(rules.get(0).getDepartment());
            }
        }
        return null;
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
    public abstract Ticket toTicket(
            com.cityfix.backend.model.dto.request.CreateTicketRequest request,
            com.cityfix.backend.model.entity.Category category,
            com.cityfix.backend.model.entity.User user,
            org.locationtech.jts.geom.Point location,
            com.cityfix.backend.model.enums.TicketStatus status,
            boolean needsManualReview
    );
}
