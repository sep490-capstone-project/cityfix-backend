package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.ManagerTicketService;
import com.cityfix.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerTicketServiceImpl implements ManagerTicketService {

    private final TicketRepository ticketRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final UserRepository userRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final DepartmentRepository departmentRepository;
    private final StepRepository stepRepository;
    private final CategoryRepository categoryRepository;

    private final TicketMapper ticketMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final TicketStepMapper ticketStepMapper;
    private final DepartmentMapper departmentMapper;

    private final TicketService ticketService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private boolean isManager(User user) {
        return user != null && user.getRole() != null && "MANAGER".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private TicketStep createOrUpdateTicketStep(
            Ticket ticket, Step step, User assignee, AssignmentStatus status, Integer orderIndex) {
        TicketStep existingStep = ticketStepRepository
                .findByTicketTicketIdAndStepStepId(ticket.getTicketId(), step.getStepId())
                .orElse(null);

        if (existingStep != null) {
            existingStep.setAssignee(assignee);
            existingStep.setStatus(status);
            if (orderIndex != null) {
                existingStep.setOrderIndex(orderIndex);
            }
            return ticketStepRepository.save(existingStep);
        } else {
            TicketStep newStep = ticketStepMapper.toTicketStep(ticket, step, assignee, status, orderIndex);
            return ticketStepRepository.save(newStep);
        }
    }

    private void cascadeCancelTicketSteps(UUID ticketId) {
        List<TicketStep> steps = ticketStepRepository.findByTicketTicketId(ticketId);
        LocalDateTime now = LocalDateTime.now();
        List<TicketStep> stepsToCancel = new ArrayList<>();
        for (TicketStep step : steps) {
            if (step.getStatus() != AssignmentStatus.CANCELLED) {
                step.setStatus(AssignmentStatus.CANCELLED);
                if (step.getCompletedAt() == null) {
                    step.setCompletedAt(now);
                }
                step.setUpdatedAt(now);
                stepsToCancel.add(step);
            }
        }
        if (!stepsToCancel.isEmpty()) {
            ticketStepRepository.saveAll(stepsToCancel);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getInitiatedTickets(Pageable pageable) {
        Page<Ticket> tickets = ticketRepository.findByStatus(TicketStatus.INITIATED, pageable);
        Page<TicketResponse> responses = tickets.map(ticketMapper::toTicketResponse);
        enrichInitiatedTicketsDepartment(tickets.getContent(), responses.getContent());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getDepartmentTickets(
            UUID departmentId,
            SeverityLevel severity,
            Priority priority,
            TicketStatus status,
            Pageable pageable) {
        User currentUser = getCurrentUser();
        boolean isMgr = isManager(currentUser);
        boolean isAdm = isAdmin(currentUser);

        if (!isMgr && !isAdm) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        UUID targetDeptId;
        if (isMgr) {
            if (currentUser.getDepartment() == null || currentUser.getDepartment().getDepartmentId() == null) {
                throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
            }
            UUID managerDeptId = currentUser.getDepartment().getDepartmentId();
            if (departmentId != null && !departmentId.equals(managerDeptId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            targetDeptId = managerDeptId;
        } else {
            // ADMIN
            if (departmentId != null) {
                targetDeptId = departmentId;
            } else if (currentUser.getDepartment() != null) {
                targetDeptId = currentUser.getDepartment().getDepartmentId();
            } else {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }

        Page<Ticket> tickets = ticketRepository.findTicketsByDepartmentAndCriteria(
                targetDeptId, severity, priority, status, pageable
        );
        Page<TicketResponse> responses = tickets.map(ticketMapper::toTicketResponse);

        Department targetDept = departmentRepository.findById(targetDeptId).orElse(null);
        String targetDeptName = targetDept != null ? targetDept.getName() : (currentUser.getDepartment() != null ? currentUser.getDepartment().getName() : null);

        responses.forEach(res -> {
            if (res.getDepartmentId() == null) {
                res.setDepartmentId(targetDeptId);
                res.setDepartmentName(targetDeptName);
            }
        });

        return responses;
    }

    private void enrichInitiatedTicketsDepartment(List<Ticket> tickets, List<TicketResponse> responses) {
        if (tickets == null || responses == null || tickets.isEmpty() || responses.isEmpty()) {
            return;
        }

        Set<UUID> categoryIdsToFetch = new HashSet<>();
        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            TicketResponse res = responses.get(i);
            if (res.getDepartmentId() == null && t.getCategory() != null && t.getCategory().getCategoryId() != null) {
                categoryIdsToFetch.add(t.getCategory().getCategoryId());
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

        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            TicketResponse res = responses.get(i);
            if (res.getDepartmentId() == null && t.getCategory() != null) {
                List<RoutingRule> categoryRules = rulesByCategory.get(t.getCategory().getCategoryId());
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

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getSuggestedDepartments(UUID ticketId, UUID categoryId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        UUID searchCategoryId = categoryId != null ? categoryId : (ticket.getCategory() != null ? ticket.getCategory().getCategoryId() : null);
        if (searchCategoryId == null) {
            return Collections.emptyList();
        }

        List<RoutingRule> rules = routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(searchCategoryId);
        String address = ticket.getAddress() != null ? ticket.getAddress().toLowerCase() : "";

        List<Department> suggestedDepts = rules.stream()
                .filter(rule -> {
                    boolean provinceMatch = rule.getProvince() == null || rule.getProvince().isBlank() || address.contains(rule.getProvince().toLowerCase());
                    boolean wardMatch = rule.getWard() == null || rule.getWard().isBlank() || address.contains(rule.getWard().toLowerCase());
                    return provinceMatch && wardMatch;
                })
                .map(RoutingRule::getDepartment)
                .distinct()
                .toList();

        return departmentMapper.toResponseList(suggestedDepts);
    }

    @Override
    @Transactional
    public TicketResponse reviewTicket(UUID ticketId, ReviewTicketRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getStatus() != TicketStatus.INITIATED) {
            throw new AppException(ErrorCode.TICKET_NOT_INITIATED);
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            ticket.setCategory(category);
        }

        if (request.getIsLifeThreatening() != null) {
            ticket.setIsLifeThreatening(request.getIsLifeThreatening());
        }

        if (request.getReviewNote() != null) {
            ticket.setReviewNote(request.getReviewNote());
        }

        UUID targetDeptId = request.getTargetDepartmentId();
        List<ReviewTicketRequest.SubCategoryRouteRequest> routes = request.getRoutes();

        boolean hasTargetDept = targetDeptId != null;
        boolean hasRoutes = routes != null && !routes.isEmpty();

        if (hasTargetDept == hasRoutes) {
            throw new AppException(ErrorCode.INVALID_ROUTING_CONFIGURATION, "Phải cung cấp duy nhất targetDepartmentId hoặc danh sách routes");
        }

        int index = ticket.getSteps() != null ? ticket.getSteps().size() : 0;
        List<TicketStep> createdSteps = new ArrayList<>();

        if (hasTargetDept) {
            User staff = userRepository.findRepresentativeStaffByDepartmentId(targetDeptId)
                    .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

            if (ticket.getSubCategories() != null) {
                for (TicketSubCategory tsc : ticket.getSubCategories()) {
                    if (tsc.getSubCategory() != null) {
                        List<Step> steps = stepRepository.findBySubCategory_SubCategoryId(tsc.getSubCategory().getSubCategoryId());
                        for (Step s : steps) {
                            createdSteps.add(createOrUpdateTicketStep(ticket, s, null, AssignmentStatus.OPEN, null));
                        }
                    }
                }
            }
        } else {
            Set<UUID> validSubCategoryIds = ticket.getSubCategories() != null
                    ? ticket.getSubCategories().stream()
                            .filter(tsc -> tsc.getSubCategory() != null)
                            .map(tsc -> tsc.getSubCategory().getSubCategoryId())
                            .collect(Collectors.toSet())
                    : Collections.emptySet();

            Map<UUID, User> deptStaffCache = new HashMap<>();
            for (ReviewTicketRequest.SubCategoryRouteRequest route : routes) {
                if (route.getSubCategoryId() == null || route.getDepartmentId() == null) {
                    throw new AppException(ErrorCode.INVALID_ROUTING_CONFIGURATION);
                }
                if (!validSubCategoryIds.contains(route.getSubCategoryId())) {
                    throw new AppException(ErrorCode.INVALID_REQUEST);
                }
                User staff = deptStaffCache.computeIfAbsent(route.getDepartmentId(), dId ->
                        userRepository.findRepresentativeStaffByDepartmentId(dId)
                                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND)));

                List<Step> steps = stepRepository.findBySubCategory_SubCategoryId(route.getSubCategoryId());
                for (Step s : steps) {
                    createdSteps.add(createOrUpdateTicketStep(ticket, s, null, AssignmentStatus.OPEN, null));
                }
            }
        }

        ticket.setSteps(createdSteps);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setNeedsManualReview(false);

        // Calculate and set duration
        ticket.setDuration(ticketService.calculateTicketDuration(ticket));

        ticket = ticketRepository.save(ticket);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.ROUTED, TicketStatus.INITIATED, TicketStatus.OPEN, "Duyệt thủ công vé thành công"
        );
        ticketHistoryRepository.save(history);

        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse rejectInitiatedTicket(UUID ticketId, String reason) {
        User currentUser = getCurrentUser();

        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getStatus() != TicketStatus.INITIATED) {
            throw new AppException(ErrorCode.TICKET_NOT_INITIATED);
        }

        String finalReason = (reason == null || reason.trim().isEmpty()) ? "Không có lý do từ chối" : reason;

        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setNeedsManualReview(false);
        ticket.setReviewNote(finalReason);

        cascadeCancelTicketSteps(ticketId);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REJECTED, TicketStatus.INITIATED, TicketStatus.REJECTED, finalReason
        );
        ticketHistoryRepository.save(history);

        ticket = ticketRepository.save(ticket);
        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketStepResponse updateTicketStepDeadline(UUID ticketStepId, UpdateTicketStepDeadlineRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (request.getDeadline() != null && request.getDeadline().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        TicketStep ticketStep = ticketStepRepository.findById(ticketStepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        ticketStepMapper.updateDeadline(request, ticketStep);
        ticketStep = ticketStepRepository.save(ticketStep);

        // If ticket has multiple subcategories, recalculate duration
        Ticket ticket = ticketStep.getTicket();
        if (ticket != null && ticket.getSubCategories() != null && ticket.getSubCategories().size() > 1) {
            ticket.setDuration(ticketService.calculateTicketDuration(ticket));
            ticketRepository.save(ticket);
        }

        return ticketStepMapper.toTicketStepResponse(ticketStep);
    }

    @Override
    @Transactional
    public List<TicketStepResponse> updateTicketStepDeadlines(UUID ticketId, UpdateTicketStepDeadlinesRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(ticketId);
        Map<UUID, TicketStep> stepMap = steps.stream()
                .collect(Collectors.toMap(TicketStep::getId, java.util.function.Function.identity()));

        for (StepDeadlineItem item : request.getStepDeadlines()) {
            if (item.getDeadline() != null && item.getDeadline().isBefore(LocalDateTime.now())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            TicketStep step = stepMap.get(item.getTicketStepId());
            if (step == null) {
                throw new AppException(ErrorCode.STEP_NOT_FOUND);
            }
            step.setDeadline(item.getDeadline());
        }

        steps = ticketStepRepository.saveAll(steps);
        ticket.setSteps(steps);

        // If ticket has multiple subcategories, recalculate duration
        if (ticket.getSubCategories() != null && ticket.getSubCategories().size() > 1) {
            ticket.setDuration(ticketService.calculateTicketDuration(ticket));
            ticketRepository.save(ticket);
        }

        return ticketStepMapper.toTicketStepResponseList(steps);
    }

    @Override
    @Transactional
    public TicketStepResponse updateTicketStepOrderIndex(UUID ticketStepId, UpdateTicketStepOrderRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        TicketStep ticketStep = ticketStepRepository.findById(ticketStepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        ticketStepMapper.updateOrderIndex(request, ticketStep);
        ticketStep = ticketStepRepository.save(ticketStep);

        return ticketStepMapper.toTicketStepResponse(ticketStep);
    }

    @Override
    @Transactional
    public List<TicketStepResponse> reorderTicketSteps(UUID ticketId, ReorderTicketStepsRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(ticketId);
        Map<UUID, TicketStep> stepMap = steps.stream()
                .collect(Collectors.toMap(TicketStep::getId, java.util.function.Function.identity()));

        for (StepOrderItem item : request.getStepOrders()) {
            TicketStep step = stepMap.get(item.getTicketStepId());
            if (step == null) {
                throw new AppException(ErrorCode.STEP_NOT_FOUND);
            }
            step.setOrderIndex(item.getOrderIndex());
        }

        List<TicketStep> sortedSteps = steps.stream()
                .sorted(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        sortedSteps = ticketStepRepository.saveAll(sortedSteps);

        return ticketStepMapper.toTicketStepResponseList(sortedSteps);
    }

    @Override
    @Transactional
    public TicketResponse updateTicketDuration(UUID ticketId, UpdateTicketDurationRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getSubCategories() != null && ticket.getSubCategories().size() > 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Không thể cập nhật thủ công duration cho ticket có nhiều subcategory");
        }

        ticket.setDuration(request.getDuration());
        ticket = ticketRepository.save(ticket);

        return ticketMapper.toTicketResponse(ticket);
    }
}
