package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.ManagerDepartmentResponse;
import com.cityfix.backend.model.dto.response.SuggestedAssigneeResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.EmailService;
import com.cityfix.backend.service.ManagerTicketService;
import com.cityfix.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
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
    private final SubCategoryRepository subCategoryRepository;
    private final UserDepartmentRepository userDepartmentRepository;

    private final TicketMapper ticketMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final TicketStepMapper ticketStepMapper;
    private final DepartmentMapper departmentMapper;

    private final TicketService ticketService;
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Object principal = auth.getPrincipal();
        String email = auth.getName();
        if (principal instanceof User user && user.getEmail() != null) {
            email = user.getEmail();
        }
        return userRepository.findByEmail(email)
                .or(() -> principal instanceof User user ? Optional.of(user) : Optional.empty())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private boolean isManager(User user) {
        if (user != null && user.getRole() != null) {
            try {
                if ("MANAGER".equalsIgnoreCase(user.getRole().getRoleName())) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equalsIgnoreCase("ROLE_MANAGER") || a.getAuthority().equalsIgnoreCase("MANAGER"));
        }
        return false;
    }

    private boolean isAdmin(User user) {
        if (user != null && user.getRole() != null) {
            try {
                if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equalsIgnoreCase("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
        }
        return false;
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
            List<Department> mgrDepts = userDepartmentRepository.findDepartmentsByUserId(currentUser.getUserId());
            if (mgrDepts.isEmpty() && currentUser.getDepartment() != null && currentUser.getDepartment().getDepartmentId() != null) {
                mgrDepts = List.of(currentUser.getDepartment());
            }
            if (mgrDepts.isEmpty()) {
                throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
            }
            Set<UUID> mgrDeptIds = mgrDepts.stream().map(Department::getDepartmentId).collect(Collectors.toSet());
            if (departmentId != null) {
                if (!mgrDeptIds.contains(departmentId)) {
                    throw new AppException(ErrorCode.FORBIDDEN);
                }
                targetDeptId = departmentId;
            } else {
                targetDeptId = mgrDepts.get(0).getDepartmentId();
            }
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
            UUID catId = getTicketCategoryId(t);
            if (res.getDepartmentId() == null && catId != null) {
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

        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            TicketResponse res = responses.get(i);
            UUID catId = getTicketCategoryId(t);
            if (res.getDepartmentId() == null && catId != null) {
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

    @Override
    @Transactional(readOnly = true)
    public List<ManagerDepartmentResponse> getManagerDepartments() {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        List<Department> departments = userDepartmentRepository.findDepartmentsByUserId(currentUser.getUserId());
        if (departments.isEmpty() && currentUser.getDepartment() != null) {
            departments = List.of(currentUser.getDepartment());
        }

        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        List<ManagerDepartmentResponse> result = new ArrayList<>();
        for (Department dept : departments) {
            List<User> activeStaffs = userDepartmentRepository.findActiveStaffByDepartmentId(dept.getDepartmentId());
            if (activeStaffs.isEmpty()) {
                activeStaffs = userRepository.findActiveStaffByDepartmentId(dept.getDepartmentId());
            }

            List<ManagerDepartmentResponse.StaffSummaryResponse> staffResponses = activeStaffs.stream()
                    .map(staff -> ManagerDepartmentResponse.StaffSummaryResponse.builder()
                            .userId(staff.getUserId())
                            .username(staff.getEmail())
                            .fullName(staff.getFullName())
                            .email(staff.getEmail())
                            .phoneNumber(staff.getPhoneNumber())
                            .build())
                    .toList();

            result.add(ManagerDepartmentResponse.builder()
                    .departmentId(dept.getDepartmentId())
                    .departmentName(dept.getName())
                    .level(dept.getLevel())
                    .contactEmail(dept.getContactEmail())
                    .phoneNumber(dept.getPhoneNumber())
                    .staffs(staffResponses)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional
    public TicketResponse reviewTicket(UUID ticketId, ReviewTicketRequest request) {
        // =========================================================================
        // 1. Phân quyền: CHỈ cho phép role MANAGER thực hiện
        // =========================================================================
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // =========================================================================
        // 2. Tìm kiếm vé & kiểm tra trạng thái INITIATED
        // =========================================================================
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getStatus() != TicketStatus.INITIATED) {
            throw new AppException(ErrorCode.TICKET_NOT_INITIATED);
        }

        // =========================================================================
        // 3. Validation danh sách SubCategory
        // =========================================================================
        List<SubCategory> subCategories = validateAndFetchSubCategories(request.getSubCategoryIds());

        // =========================================================================
        // 4. Validation danh sách steps & assignees
        // =========================================================================
        Map<UUID, Step> stepMap = validateAndFetchSteps(request.getSteps());
        Map<UUID, User> assigneeMap = validateAndFetchAssignees(request.getSteps());

        // =========================================================================
        // 5. Cập nhật SOS và Review Note (KHÔNG đổi categoryId theo thiết kế mới)
        // =========================================================================
        if (request.getIsLifeThreatening() != null) {
            ticket.setIsLifeThreatening(request.getIsLifeThreatening());
        }
        if (request.getReviewNote() != null) {
            ticket.setReviewNote(request.getReviewNote());
        }

        // =========================================================================
        // 6. Xử lý Hibernate Collection: Ticket_SubCategory (orphanRemoval = true)
        // =========================================================================
        updateTicketSubCategories(ticket, subCategories);

        // =========================================================================
        // 7. Xử lý Hibernate Collection: Ticket_Step (orphanRemoval = true)
        // =========================================================================
        updateTicketSteps(ticket, request.getSteps(), stepMap, assigneeMap);

        // =========================================================================
        // 8. Chuyển trạng thái vé: IN_PROGRESS theo chỉ thị thiết kế
        // =========================================================================
        TicketStatus prevStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setNeedsManualReview(false);

        // =========================================================================
        // 9. Tính toán hạn chót (deadline) của Ticket từ hạn chót trễ nhất của các bước
        // =========================================================================
        ticket.setDeadline(resolveTicketDeadline(ticket));

        // =========================================================================
        // 10. Lưu Ticket xuống Database (Cascade lưu TicketSubCategory & TicketStep)
        // =========================================================================
        ticket = ticketRepository.save(ticket);

        // =========================================================================
        // 11. Ghi log lịch sử TicketHistory
        // =========================================================================
        String historyNote = (request.getReviewNote() != null && !request.getReviewNote().isBlank())
                ? request.getReviewNote()
                : "Duyệt thủ công vé thành công, chuyển sang Đang xử lý";
        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.ROUTED, prevStatus, TicketStatus.IN_PROGRESS, historyNote
        );
        ticketHistoryRepository.save(history);

        // =========================================================================
        // 12. Gửi thông báo WebSocket & Email cho người tạo vé (Creator)
        // =========================================================================
        sendReviewSuccessNotification(ticket);

        return ticketMapper.toTicketResponse(ticket);
    }

    private LocalDateTime resolveTicketDeadline(Ticket ticket) {
        if (ticket == null) return null;
        LocalDateTime calculated = ticketService.calculateTicketDeadline(ticket);
        if (calculated != null) {
            return calculated;
        }
        if (ticket.getSteps() != null && !ticket.getSteps().isEmpty()) {
            return ticket.getSteps().stream()
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isDeleted())
                    .filter(s -> s.getStatus() != AssignmentStatus.CANCELLED && s.getStatus() != AssignmentStatus.TRANSFERRED)
                    .map(TicketStep::getDeadline)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }
        return null;
    }

    private List<SubCategory> validateAndFetchSubCategories(List<UUID> subCategoryIds) {
        if (subCategoryIds == null || subCategoryIds.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Danh sách sự cố phụ không được để trống");
        }
        Set<UUID> uniqueSubCatIds = new HashSet<>(subCategoryIds);
        if (uniqueSubCatIds.size() != subCategoryIds.size()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Danh sách sự cố phụ không được chứa ID trùng lặp");
        }
        List<SubCategory> subCategories = subCategoryRepository.findAllById(subCategoryIds);
        if (subCategories.size() != subCategoryIds.size()) {
            throw new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND);
        }
        for (SubCategory sc : subCategories) {
            if (!sc.isActive() || sc.isDeleted()) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Sự cố phụ '" + sc.getName() + "' đã bị vô hiệu hóa hoặc bị xóa");
            }
        }
        return subCategories;
    }

    private Map<UUID, Step> validateAndFetchSteps(List<ReviewTicketRequest.StepAssignmentRequest> stepRequests) {
        if (stepRequests == null || stepRequests.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Danh sách bước xử lý không được để trống");
        }
        Set<UUID> uniqueStepIds = new HashSet<>();
        for (ReviewTicketRequest.StepAssignmentRequest stepReq : stepRequests) {
            if (!uniqueStepIds.add(stepReq.getStepId())) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Danh sách bước xử lý chứa stepId trùng lặp: " + stepReq.getStepId());
            }
            if (stepReq.getDeadline() != null && stepReq.getDeadline().isBefore(LocalDateTime.now())) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Hạn chót của bước xử lý không được ở quá khứ");
            }
        }
        List<Step> stepEntities = stepRepository.findAllById(uniqueStepIds);
        if (stepEntities.size() != uniqueStepIds.size() || stepEntities.stream().anyMatch(Step::isDeleted)) {
            throw new AppException(ErrorCode.STEP_NOT_FOUND);
        }
        return stepEntities.stream().collect(Collectors.toMap(Step::getStepId, Function.identity()));
    }

    private Map<UUID, User> validateAndFetchAssignees(List<ReviewTicketRequest.StepAssignmentRequest> stepRequests) {
        Set<UUID> assigneeIds = stepRequests.stream()
                .map(ReviewTicketRequest.StepAssignmentRequest::getAssigneeId)
                .collect(Collectors.toSet());
        List<User> assignees = userRepository.findAllById(assigneeIds);
        if (assignees.size() != assigneeIds.size()) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Không tìm thấy một số nhân viên được phân công");
        }
        for (User staff : assignees) {
            if (staff.isDeleted() || staff.getStatus() != com.cityfix.backend.model.enums.UserStatus.ACTIVE) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Nhân viên '" + staff.getFullName() + "' không còn hoạt động");
            }
        }

        // Batch query kiểm tra phòng ban qua bảng user_department để tránh N+1 query
        Set<UUID> staffsToCheck = assignees.stream()
                .filter(staff -> staff.getDepartment() == null)
                .map(User::getUserId)
                .collect(Collectors.toSet());

        Set<UUID> userIdsWithNNDept = staffsToCheck.isEmpty()
                ? Collections.emptySet()
                : userDepartmentRepository.findUserIdsWithDepartment(staffsToCheck);

        for (User staff : assignees) {
            boolean hasDept = staff.getDepartment() != null || userIdsWithNNDept.contains(staff.getUserId());
            if (!hasDept) {
                throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT, "Nhân viên '" + staff.getFullName() + "' không thuộc phòng ban nào");
            }
        }
        return assignees.stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
    }

    private void updateTicketSubCategories(Ticket ticket, List<SubCategory> subCategories) {
        if (ticket.getSubCategories() == null) {
            ticket.setSubCategories(new ArrayList<>());
        }

        Set<UUID> targetSubCatIds = subCategories.stream()
                .map(SubCategory::getSubCategoryId)
                .collect(Collectors.toSet());

        // 1. Loại bỏ các subcategory không còn nằm trong danh sách review
        ticket.getSubCategories().removeIf(tsc ->
                tsc.getSubCategory() == null || !targetSubCatIds.contains(tsc.getSubCategory().getSubCategoryId()));

        // 2. Xác định các subcategory đã tồn tại sẵn để giữ nguyên (tránh vi phạm unique constraint uk_ticket_subcategory)
        Set<UUID> existingSubCatIds = ticket.getSubCategories().stream()
                .filter(tsc -> tsc.getSubCategory() != null)
                .map(tsc -> tsc.getSubCategory().getSubCategoryId())
                .collect(Collectors.toSet());

        // 3. Chỉ thêm mới những subcategory chưa từng có
        for (SubCategory sc : subCategories) {
            if (!existingSubCatIds.contains(sc.getSubCategoryId())) {
                TicketSubCategory tsc = TicketSubCategory.builder()
                        .ticket(ticket)
                        .subCategory(sc)
                        .build();
                ticket.getSubCategories().add(tsc);
            }
        }
    }

    private void updateTicketSteps(Ticket ticket, List<ReviewTicketRequest.StepAssignmentRequest> stepRequests,
                                   Map<UUID, Step> stepMap, Map<UUID, User> assigneeMap) {
        if (ticket.getSteps() == null) {
            ticket.setSteps(new ArrayList<>());
        }

        Set<UUID> targetStepIds = stepRequests.stream()
                .map(ReviewTicketRequest.StepAssignmentRequest::getStepId)
                .collect(Collectors.toSet());

        // 1. Loại bỏ các step không còn nằm trong danh sách review
        ticket.getSteps().removeIf(ts -> ts.getStep() == null || !targetStepIds.contains(ts.getStep().getStepId()));

        Map<UUID, TicketStep> existingStepMap = ticket.getSteps().stream()
                .filter(ts -> ts.getStep() != null)
                .collect(Collectors.toMap(ts -> ts.getStep().getStepId(), Function.identity(), (a, b) -> a));

        // 2. Sắp xếp theo orderIndex tăng dần để tính nối tiếp startedAt và deadline
        List<ReviewTicketRequest.StepAssignmentRequest> sortedRequests = stepRequests.stream()
                .sorted(Comparator.comparing(ReviewTicketRequest.StepAssignmentRequest::getOrderIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime prevDeadline = now;

        for (ReviewTicketRequest.StepAssignmentRequest stepReq : sortedRequests) {
            Step step = stepMap.get(stepReq.getStepId());
            User assignee = assigneeMap.get(stepReq.getAssigneeId());
            LocalDateTime stepStart = prevDeadline;
            LocalDateTime deadline = stepReq.getDeadline();
            if (deadline == null && step != null && step.getDuration() != null && step.getDuration() > 0) {
                deadline = stepStart.plusHours(step.getDuration());
            }
            if (deadline != null) {
                prevDeadline = deadline;
            }

            TicketStep ticketStep = existingStepMap.get(stepReq.getStepId());
            if (ticketStep != null) {
                ticketStep.setAssignee(assignee);
                ticketStep.setStatus(AssignmentStatus.OPEN);
                ticketStep.setOrderIndex(stepReq.getOrderIndex());
                ticketStep.setStartedAt(stepStart);
                ticketStep.setDeadline(deadline);
                ticketStep.setNotes(stepReq.getNotes());
                ticketStep.setUpdatedAt(now);
            } else {
                ticketStep = ticketStepMapper.toTicketStepWithTimeline(
                        ticket, step, assignee, AssignmentStatus.OPEN,
                        stepReq.getOrderIndex(), stepStart, deadline, stepReq.getNotes()
                );
                if (ticketStep == null) {
                    ticketStep = TicketStep.builder()
                            .ticket(ticket)
                            .step(step)
                            .assignee(assignee)
                            .status(AssignmentStatus.OPEN)
                            .orderIndex(stepReq.getOrderIndex())
                            .startedAt(stepStart)
                            .deadline(deadline)
                            .notes(stepReq.getNotes())
                            .build();
                } else {
                    ticketStep.setStartedAt(stepStart);
                    ticketStep.setDeadline(deadline);
                    ticketStep.setNotes(stepReq.getNotes());
                }
                ticket.getSteps().add(ticketStep);
            }
        }
    }

    private void sendReviewSuccessNotification(Ticket ticket) {
        User creator = ticket.getUser();
        if (creator == null) return;

        String title = "Sự cố của bạn đã được tiếp nhận và xử lý";
        String message = "Phản ánh của bạn đã được quản lý phê duyệt và chuyển cho cán bộ chuyên trách tiến hành xử lý.";

        Notification notification = Notification.builder()
                .user(creator)
                .title(title)
                .content(message)
                .isRead(false)
                .build();
        notification = notificationRepository.save(notification);

        try {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + creator.getUserId(),
                    notificationMapper.toResponse(notification)
            );
        } catch (Exception e) {
            log.error("Lỗi khi gửi WebSocket notification cho vé {}: {}", ticket.getTicketId(), e.getMessage());
        }

        if (creator.getEmail() != null && !creator.getEmail().isBlank()) {
            try {
                emailService.sendTicketStatusEmail(creator.getEmail(), title, message);
            } catch (Exception e) {
                log.error("Lỗi khi gửi email notification cho vé {}: {}", ticket.getTicketId(), e.getMessage());
            }
        }
    }

    private void sendRejectNotification(Ticket ticket, String reason) {
        User creator = ticket.getUser();
        if (creator == null) return;

        String title = "Sự cố của bạn đã bị từ chối tiếp nhận";
        String message = "Phản ánh của bạn đã bị từ chối với lý do: " + reason;

        Notification notification = Notification.builder()
                .user(creator)
                .title(title)
                .content(message)
                .isRead(false)
                .build();
        notification = notificationRepository.save(notification);

        try {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + creator.getUserId(),
                    notificationMapper.toResponse(notification)
            );
        } catch (Exception e) {
            log.error("Lỗi khi gửi WebSocket notification cho vé {}: {}", ticket.getTicketId(), e.getMessage());
        }

        if (creator.getEmail() != null && !creator.getEmail().isBlank()) {
            try {
                emailService.sendTicketStatusEmail(creator.getEmail(), title, message);
            } catch (Exception e) {
                log.error("Lỗi khi gửi email notification cho vé {}: {}", ticket.getTicketId(), e.getMessage());
            }
        }
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
        sendRejectNotification(ticket, finalReason);
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

        LocalDateTime now = LocalDateTime.now();
        ticketStepMapper.updateDeadline(request, ticketStep);
        if (request.getDeadline() != null) {
            ticketStep.setDeadline(request.getDeadline());
        }
        ticketStep.setUpdatedAt(now);
        ticketStep = ticketStepRepository.save(ticketStep);

        Ticket ticket = ticketStep.getTicket();
        if (ticket != null) {
            List<TicketStep> steps = null;
            if (ticket.getTicketId() != null) {
                steps = ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(ticket.getTicketId());
            }
            if (steps == null || steps.isEmpty()) {
                steps = ticket.getSteps();
            }
            if (steps != null && !steps.isEmpty()) {
                List<TicketStep> cascaded = cascadeSubsequentStepDeadlines(steps, ticketStep, now);
                if (!cascaded.isEmpty()) {
                    ticketStepRepository.saveAll(cascaded);
                }
                ticket.setSteps(steps);
            }
            ticket.setDeadline(resolveTicketDeadline(ticket));
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

        Map<UUID, LocalDateTime> requestedDeadlines = new HashMap<>();
        for (StepDeadlineItem item : request.getStepDeadlines()) {
            if (item.getDeadline() != null && item.getDeadline().isBefore(LocalDateTime.now())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
            TicketStep step = stepMap.get(item.getTicketStepId());
            if (step == null) {
                throw new AppException(ErrorCode.STEP_NOT_FOUND);
            }
            requestedDeadlines.put(item.getTicketStepId(), item.getDeadline());
        }

        LocalDateTime now = LocalDateTime.now();
        List<TicketStep> sortedSteps = steps.stream()
                .sorted(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        boolean cascade = false;
        LocalDateTime prevDeadline = null;
        for (TicketStep step : sortedSteps) {
            if (requestedDeadlines.containsKey(step.getId())) {
                LocalDateTime newDeadline = requestedDeadlines.get(step.getId());
                if (cascade && prevDeadline != null && isStepPendingOrActive(step)) {
                    step.setStartedAt(prevDeadline);
                }
                step.setDeadline(newDeadline);
                step.setUpdatedAt(now);
                prevDeadline = newDeadline;
                cascade = true;
            } else if (cascade && prevDeadline != null && isStepPendingOrActive(step)) {
                int duration = (step.getStep() != null && step.getStep().getDuration() != null && step.getStep().getDuration() > 0)
                        ? step.getStep().getDuration()
                        : 0;
                LocalDateTime nextStart = prevDeadline;
                LocalDateTime nextDeadline = nextStart.plusHours(duration);
                ticketStepMapper.updateTicketStepTimeline(nextStart, nextDeadline, now, step);
                step.setStartedAt(nextStart);
                step.setDeadline(nextDeadline);
                step.setUpdatedAt(now);
                prevDeadline = nextDeadline;
            } else {
                prevDeadline = step.getDeadline();
            }
        }

        steps = ticketStepRepository.saveAll(steps);

        ticket.setSteps(steps);
        ticket.setDeadline(resolveTicketDeadline(ticket));
        ticketRepository.save(ticket);

        return ticketStepMapper.toTicketStepResponseList(steps);
    }

    private boolean isStepPendingOrActive(TicketStep step) {
        if (step == null || step.isDeleted()) return false;
        AssignmentStatus status = step.getStatus();
        return status != AssignmentStatus.COMPLETED
                && status != AssignmentStatus.RESOLVED
                && status != AssignmentStatus.CANCELLED
                && status != AssignmentStatus.TRANSFERRED;
    }

    private List<TicketStep> cascadeSubsequentStepDeadlines(List<TicketStep> steps, TicketStep targetStep, LocalDateTime now) {
        List<TicketStep> sortedSteps = steps.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isDeleted())
                .sorted(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        boolean foundTarget = false;
        LocalDateTime prevDeadline = targetStep.getDeadline();
        List<TicketStep> cascaded = new ArrayList<>();

        for (TicketStep s : sortedSteps) {
            if (!foundTarget) {
                if (s == targetStep || (s.getId() != null && s.getId().equals(targetStep.getId()))) {
                    s.setDeadline(targetStep.getDeadline());
                    s.setUpdatedAt(now);
                    prevDeadline = s.getDeadline();
                    foundTarget = true;
                }
            } else if (isStepPendingOrActive(s) && prevDeadline != null) {
                int duration = (s.getStep() != null && s.getStep().getDuration() != null && s.getStep().getDuration() > 0)
                        ? s.getStep().getDuration()
                        : 0;
                LocalDateTime nextStart = prevDeadline;
                LocalDateTime nextDeadline = nextStart.plusHours(duration);
                ticketStepMapper.updateTicketStepTimeline(nextStart, nextDeadline, now, s);
                s.setStartedAt(nextStart);
                s.setDeadline(nextDeadline);
                s.setUpdatedAt(now);
                prevDeadline = nextDeadline;
                cascaded.add(s);
            }
        }
        return cascaded;
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
}
