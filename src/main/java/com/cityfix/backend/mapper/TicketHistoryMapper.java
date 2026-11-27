package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.TicketHistoryResponse;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketHistory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.TicketStatus;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketHistoryMapper {

    @Mapping(target = "historyId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "ticket", source = "ticket")
    @Mapping(target = "actor", source = "actor")
    @Mapping(target = "actionType", source = "actionType")
    @Mapping(target = "previousStatus", source = "previousStatus")
    @Mapping(target = "currentStatus", source = "currentStatus")
    @Mapping(target = "actionNote", source = "actionNote")
    TicketHistory toTicketHistory(
            Ticket ticket,
            User actor,
            ActionType actionType,
            TicketStatus previousStatus,
            TicketStatus currentStatus,
            String actionNote
    );

    @Mapping(target = "ticketId", source = "ticket.ticketId")
    @Mapping(target = "actorId", source = "actor.userId")
    @Mapping(target = "actorName", source = "actor.fullName")
    TicketHistoryResponse toTicketHistoryResponse(TicketHistory history);

    List<TicketHistoryResponse> toTicketHistoryResponseList(List<TicketHistory> histories);

    @AfterMapping
    default void hideAnonymousReporter(TicketHistory history, @MappingTarget TicketHistoryResponse response) {
        if (history == null || response == null) {
            return;
        }
        Ticket ticket = history.getTicket();
        if (ticket == null || !Boolean.TRUE.equals(ticket.getIsAnonymous())) {
            return;
        }

        User reporter = ticket.getUser();
        User actor = history.getActor();
        boolean isReporterActor = reporter != null
                && actor != null
                && reporter.getUserId() != null
                && reporter.getUserId().equals(actor.getUserId());

        if (!isReporterActor) {
            return;
        }

        boolean isAdmin = false;
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                boolean hasAdminAuthority = auth.getAuthorities() != null && auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                                || a.getAuthority().equalsIgnoreCase("ADMIN"));

                boolean hasAdminPrincipal = false;
                Object principal = auth.getPrincipal();
                if (principal instanceof User u) {
                    hasAdminPrincipal = u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getRoleName());
                }

                isAdmin = hasAdminAuthority || hasAdminPrincipal;
            }
        } catch (Exception ignored) {
            isAdmin = false;
        }

        if (!isAdmin) {
            response.setActorId(null);
            response.setActorName(null);
        }
    }
}
