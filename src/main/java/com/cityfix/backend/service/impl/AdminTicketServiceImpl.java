package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.RatingMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.*;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.repository.specification.TicketSpecification;
import com.cityfix.backend.service.AdminTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

    private final TicketRepository ticketRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final RatingRepository ratingRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final RoutingRuleRepository routingRuleRepository;

    private final TicketMapper ticketMapper;
    private final TicketStepMapper ticketStepMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final RatingMapper ratingMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getAllTickets(AdminTicketFilterRequest filter, Pageable pageable) {
        Page<Ticket> tickets = ticketRepository.findAll(TicketSpecification.withFilter(filter), pageable);
        Page<TicketResponse> responses = tickets.map(ticket -> {
            TicketResponse res = ticketMapper.toTicketResponse(ticket);
            ensureAdminCreatorVisibility(ticket, res);
            enrichDepartmentFromUserDepartmentsIfNeeded(ticket, res);
            return res;
        });
        enrichUnassignedTicketsDepartmentFromRoutingRule(tickets.getContent(), responses.getContent());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTicketDetailResponse getTicketDetail(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        TicketResponse base = ticketMapper.toTicketResponse(ticket);
        ensureAdminCreatorVisibility(ticket, base);
        enrichDepartmentFromUserDepartmentsIfNeeded(ticket, base);
        if (base != null && base.getDepartmentId() == null) {
            enrichUnassignedTicketsDepartmentFromRoutingRule(List.of(ticket), List.of(base));
        }

        List<TicketStep> validSteps = (ticket.getSteps() != null ? ticket.getSteps() : Collections.<TicketStep>emptyList())
                .stream()
                .filter(s -> s != null && !s.isDeleted())
                .sorted(Comparator.comparing(TicketStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TicketStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<TicketStepResponse> stepResponses = ticketStepMapper.toTicketStepResponseList(validSteps);

        List<TicketHistory> histories = ticketHistoryRepository.findByTicketTicketIdOrderByCreatedAtDesc(ticketId);
        List<TicketHistoryResponse> historyResponses = ticketHistoryMapper.toTicketHistoryResponseList(histories);

        List<Rating> ratings = ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId);
        List<RatingResponse> ratingResponses = ratingMapper.toResponseList(ratings);

        long upvoteCount = ticketVoteRepository.countByTicket_TicketId(ticketId);

        User creator = ticket.getUser();

        return AdminTicketDetailResponse.builder()
                .id(ticket.getTicketId())
                .description(ticket.getDescription())
                .categories(base != null ? base.getCategories() : Collections.emptyList())
                .subCategories(base != null ? base.getSubCategories() : Collections.emptyList())
                .priority(ticket.getPriority())
                .priorityReasoning(ticket.getPriorityReasoning())
                .locationMethod(ticket.getLocationMethod())
                .address(ticket.getAddress())
                .status(ticket.getStatus())
                .latitude(ticket.getLocation() != null ? ticket.getLocation().getY() : null)
                .longitude(ticket.getLocation() != null ? ticket.getLocation().getX() : null)
                .reviewNote(ticket.getReviewNote())
                .isAnonymous(ticket.getIsAnonymous())
                .isLifeThreatening(ticket.getIsLifeThreatening())
                .needsManualReview(ticket.getNeedsManualReview())
                .createdBy(creator != null ? creator.getUserId() : null)
                .creatorFullName(creator != null ? creator.getFullName() : null)
                .creatorEmail(creator != null ? creator.getEmail() : null)
                .creatorPhoneNumber(creator != null ? creator.getPhoneNumber() : null)
                .imageUrls(base != null ? base.getImageUrls() : Collections.emptyList())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .deadline(ticket.getDeadline())
                .departmentId(base != null ? base.getDepartmentId() : null)
                .departmentName(base != null ? base.getDepartmentName() : null)
                .assigneeId(base != null ? base.getAssigneeId() : null)
                .assigneeName(base != null ? base.getAssigneeName() : null)
                .upvoteCount(upvoteCount)
                .steps(stepResponses != null ? stepResponses : Collections.emptyList())
                .histories(historyResponses != null ? historyResponses : Collections.emptyList())
                .ratings(ratingResponses != null ? ratingResponses : Collections.emptyList())
                .build();
    }

    private void ensureAdminCreatorVisibility(Ticket ticket, TicketResponse response) {
        if (ticket == null || response == null || ticket.getUser() == null) {
            return;
        }
        User creator = ticket.getUser();
        response.setCreatedBy(creator.getUserId());
        response.setCreatorFullName(creator.getFullName());
        response.setCreatorPhoneNumber(creator.getPhoneNumber());
    }

    private void enrichDepartmentFromUserDepartmentsIfNeeded(Ticket ticket, TicketResponse response) {
        if (ticket == null || response == null || response.getDepartmentId() != null) {
            return;
        }
        if (ticket.getSteps() == null || ticket.getSteps().isEmpty()) {
            return;
        }
        for (TicketStep step : ticket.getSteps()) {
            if (step != null && !step.isDeleted() && step.getAssignee() != null) {
                User assignee = step.getAssignee();
                if (response.getAssigneeId() == null) {
                    response.setAssigneeId(assignee.getUserId());
                    response.setAssigneeName(assignee.getFullName());
                }
                if (assignee.getDepartment() != null) {
                    response.setDepartmentId(assignee.getDepartment().getDepartmentId());
                    response.setDepartmentName(assignee.getDepartment().getName());
                    return;
                }
                if (assignee.getUserDepartments() != null && !assignee.getUserDepartments().isEmpty()) {
                    for (UserDepartment ud : assignee.getUserDepartments()) {
                        if (ud != null && ud.getDepartment() != null && !ud.getDepartment().isDeleted()) {
                            response.setDepartmentId(ud.getDepartment().getDepartmentId());
                            response.setDepartmentName(ud.getDepartment().getName());
                            return;
                        }
                    }
                }
            }
        }
    }

    private void enrichUnassignedTicketsDepartmentFromRoutingRule(List<Ticket> tickets, List<TicketResponse> responses) {
        if (tickets == null || responses == null || tickets.isEmpty() || responses.isEmpty()) {
            return;
        }

        Set<UUID> categoryIdsToFetch = new HashSet<>();
        for (int i = 0; i < Math.min(tickets.size(), responses.size()); i++) {
            Ticket t = tickets.get(i);
            TicketResponse res = responses.get(i);
            UUID catId = getTicketCategoryId(t);
            if (res != null && res.getDepartmentId() == null && catId != null) {
                categoryIdsToFetch.add(catId);
            }
        }

        if (categoryIdsToFetch.isEmpty()) {
            return;
        }

        List<RoutingRule> rules = routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(categoryIdsToFetch);
        if (rules == null || rules.isEmpty()) {
            return;
        }

        Map<UUID, List<RoutingRule>> rulesByCategory = rules.stream()
                .filter(r -> r.getCategory() != null && r.getCategory().getCategoryId() != null)
                .collect(Collectors.groupingBy(r -> r.getCategory().getCategoryId()));

        for (int i = 0; i < Math.min(tickets.size(), responses.size()); i++) {
            Ticket t = tickets.get(i);
            TicketResponse res = responses.get(i);
            UUID catId = getTicketCategoryId(t);
            if (res != null && res.getDepartmentId() == null && catId != null) {
                List<RoutingRule> categoryRules = rulesByCategory.get(catId);
                if (categoryRules != null && !categoryRules.isEmpty()) {
                    String address = t.getAddress() != null ? t.getAddress().toLowerCase() : "";
                    Department matchedDept = categoryRules.stream()
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
                            .orElse(categoryRules.get(0).getDepartment());

                    if (matchedDept != null) {
                        res.setDepartmentId(matchedDept.getDepartmentId());
                        res.setDepartmentName(matchedDept.getName());
                    }
                }
            }
        }
    }

    private UUID getTicketCategoryId(Ticket ticket) {
        if (ticket != null && ticket.getSubCategories() != null && !ticket.getSubCategories().isEmpty()) {
            TicketSubCategory tsc = ticket.getSubCategories().get(0);
            if (tsc != null && tsc.getSubCategory() != null && tsc.getSubCategory().getCategory() != null) {
                return tsc.getSubCategory().getCategory().getCategoryId();
            }
        }
        return null;
    }
}
