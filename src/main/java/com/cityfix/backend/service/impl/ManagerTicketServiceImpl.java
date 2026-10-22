package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.DepartmentMapper;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cityfix.backend.util.SecurityUtils;
import com.cityfix.backend.util.StepDagValidator;

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
    private final com.cityfix.backend.service.TicketWorkflowEngine ticketWorkflowEngine;
    private final com.cityfix.backend.service.NotificationService notificationService;
    private final EmailService emailService;

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .or(SecurityUtils::getCurrentUserPrincipal)
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
            Ticket ticket, Step step, User assignee, AssignmentStatus status) {
        TicketStep existingStep = ticketStepRepository
                .findByTicketTicketIdAndStepStepId(ticket.getTicketId(), step.getStepId())
                .orElse(null);

        if (existingStep != null) {
            existingStep.setAssignee(assignee);
            existingStep.setStatus(status);
            return ticketStepRepository.save(existingStep);
        } else {
            TicketStep newStep = ticketStepMapper.toTicketStep(ticket, step, assignee, status);
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
    public Page<TicketResponse> getManagerAssignedDepartmentTickets(UUID departmentId, Pageable pageable) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        List<Department> mgrDepts = new ArrayList<>(userDepartmentRepository.findDepartmentsByUserId(currentUser.getUserId()));
        if (currentUser.getDepartment() != null && currentUser.getDepartment().getDepartmentId() != null) {
            UUID directDeptId = currentUser.getDepartment().getDepartmentId();
            if (mgrDepts.stream().noneMatch(d -> d != null && directDeptId.equals(d.getDepartmentId()))) {
                mgrDepts.add(currentUser.getDepartment());
            }
        }

        if (mgrDepts.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        Set<UUID> mgrDeptIds = mgrDepts.stream()
                .filter(Objects::nonNull)
                .map(Department::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> targetDeptIds;
        if (departmentId != null) {
            if (!mgrDeptIds.contains(departmentId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            targetDeptIds = Set.of(departmentId);
        } else {
            targetDeptIds = mgrDeptIds;
        }

        Page<Ticket> tickets = ticketRepository.findTicketsByAssigneeDepartmentIds(targetDeptIds, pageable);

        return tickets.map(ticket -> {
            TicketResponse response = ticketMapper.toTicketResponse(ticket);
            if (response != null) {
                List<TicketStep> allSteps = (ticket.getSteps() != null ? ticket.getSteps() : Collections.<TicketStep>emptyList())
                        .stream()
                        .filter(s -> s != null && !s.isDeleted())
                        .sorted(Comparator.comparing(TicketStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(TicketStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                response.setSteps(ticketStepMapper.toTicketStepResponseList(allSteps));
            }
            return response;
        });
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
        // 4.1 Validate cấu trúc DAG của các bước xử lý
        // =========================================================================
        Set<UUID> allMasterStepIds = request.getSteps().stream()
                .map(ReviewTicketRequest.StepAssignmentRequest::getStepId)
                .collect(Collectors.toSet());
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        for (ReviewTicketRequest.StepAssignmentRequest stepReq : request.getSteps()) {
            Set<UUID> deps = stepReq.getDependsOnStepIds() != null
                    ? new HashSet<>(stepReq.getDependsOnStepIds())
                    : Collections.emptySet();
            adjacencyMap.put(stepReq.getStepId(), deps);
        }
        StepDagValidator.validateDag(allMasterStepIds, adjacencyMap);

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
        // 7. Xử lý Hibernate Collection: Ticket_Step (Quy trình 2 pha & kích hoạt Anchor Steps)
        // =========================================================================
        List<TicketStep> anchorSteps = updateTicketSteps(ticket, request.getSteps(), stepMap, assigneeMap);

        // =========================================================================
        // 8. Chuyển trạng thái vé: IN_PROGRESS sau khi kích hoạt các anchor step
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
                ticket, currentUser, ActionType.MANUAL_APPROVED, prevStatus, TicketStatus.IN_PROGRESS, historyNote
        );
        ticketHistoryRepository.save(history);

        // Kích hoạt thông báo và log TicketHistory cho từng Anchor Step
        for (TicketStep anchorStep : anchorSteps) {
            TicketHistory stepHistory = ticketHistoryMapper.toTicketHistory(
                    ticket, currentUser, ActionType.WORK_STARTED, TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS,
                    "Bắt đầu thực hiện bước mốc: " + (anchorStep.getStep() != null ? anchorStep.getStep().getStepName() : anchorStep.getId())
            );
            ticketHistoryRepository.save(stepHistory);
            notificationService.notifyStepStarted(anchorStep, currentUser);
        }

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
                    .filter(s -> s.getStatus() != AssignmentStatus.CANCELLED)
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

    private List<TicketStep> updateTicketSteps(Ticket ticket, List<ReviewTicketRequest.StepAssignmentRequest> stepRequests,
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

        LocalDateTime now = LocalDateTime.now();

        // Pha 1: Tạo/Cập nhật các entity TicketStep để có ID thực tế
        for (ReviewTicketRequest.StepAssignmentRequest stepReq : stepRequests) {
            Step step = stepMap.get(stepReq.getStepId());
            User assignee = assigneeMap.get(stepReq.getAssigneeId());
            LocalDateTime deadline = stepReq.getDeadline();

            TicketStep ticketStep = existingStepMap.get(stepReq.getStepId());
            if (ticketStep != null) {
                ticketStep.setAssignee(assignee);
                ticketStep.setStatus(AssignmentStatus.ASSIGNED);
                ticketStep.setDeadline(deadline);
                ticketStep.setNotes(stepReq.getNotes());
                ticketStep.setUpdatedAt(now);
            } else {
                ticketStep = ticketStepMapper.toTicketStepWithTimeline(
                        ticket, step, assignee, AssignmentStatus.ASSIGNED,
                        null, deadline, stepReq.getNotes()
                );
                if (ticketStep == null) {
                    ticketStep = TicketStep.builder()
                            .ticket(ticket)
                            .step(step)
                            .assignee(assignee)
                            .status(AssignmentStatus.ASSIGNED)
                            .deadline(deadline)
                            .notes(stepReq.getNotes())
                            .dependsOn(new HashSet<>())
                            .build();
                } else {
                    ticketStep.setDeadline(deadline);
                    ticketStep.setNotes(stepReq.getNotes());
                    if (ticketStep.getDependsOn() == null) {
                        ticketStep.setDependsOn(new HashSet<>());
                    }
                }
                ticket.getSteps().add(ticketStep);
            }
        }

        // Lưu các entity TicketStep xuống DB để sinh ID thực tế
        List<TicketStep> savedSteps = ticketStepRepository.saveAll(ticket.getSteps());
        if (ticket.getSteps() == null) {
            ticket.setSteps(new ArrayList<>(savedSteps));
        } else if (ticket.getSteps() != savedSteps) {
            List<TicketStep> copy = new ArrayList<>(savedSteps);
            ticket.getSteps().clear();
            ticket.getSteps().addAll(copy);
        }

        // Lưu map tra cứu Map<UUID, TicketStep> masterStepToTicketStepMap
        Map<UUID, TicketStep> masterStepToTicketStepMap = new HashMap<>();
        for (TicketStep ts : ticket.getSteps()) {
            if (ts.getStep() != null && ts.getStep().getStepId() != null) {
                masterStepToTicketStepMap.put(ts.getStep().getStepId(), ts);
            }
        }

        // Pha 2: Dựa trên dependsOnStepIds (danh sách master step ID), map sang TicketStep thực thể tương ứng và nạp vào step.getDependsOn()
        for (ReviewTicketRequest.StepAssignmentRequest stepReq : stepRequests) {
            TicketStep currentStep = masterStepToTicketStepMap.get(stepReq.getStepId());
            if (currentStep != null) {
                Set<TicketStep> dependsOnSet = new HashSet<>();
                if (stepReq.getDependsOnStepIds() != null) {
                    for (UUID masterDepId : stepReq.getDependsOnStepIds()) {
                        TicketStep depTicketStep = masterStepToTicketStepMap.get(masterDepId);
                        if (depTicketStep != null) {
                            dependsOnSet.add(depTicketStep);
                        }
                    }
                }
                currentStep.setDependsOn(dependsOnSet);
            }
        }

        // Kích hoạt Anchor Steps: Quét tất cả các TicketStep có dependsOn.isEmpty(), chuyển ngay sang IN_PROGRESS, set deadline
        List<TicketStep> anchorSteps = new ArrayList<>();
        for (TicketStep step : ticket.getSteps()) {
            if (step.getDependsOn() == null || step.getDependsOn().isEmpty()) {
                step.setStatus(AssignmentStatus.IN_PROGRESS);
                step.setStartedAt(now);
                if (step.getDeadline() == null && step.getStep() != null && step.getStep().getDuration() != null && step.getStep().getDuration() > 0) {
                    step.setDeadline(now.plusHours(step.getStep().getDuration()));
                }
                anchorSteps.add(step);
            } else {
                step.setStatus(AssignmentStatus.ASSIGNED);
                step.setStartedAt(null);
            }
            step.setUpdatedAt(now);
        }

        savedSteps = ticketStepRepository.saveAll(ticket.getSteps());
        if (ticket.getSteps() == null) {
            ticket.setSteps(new ArrayList<>(savedSteps));
        } else if (ticket.getSteps() != savedSteps) {
            List<TicketStep> copy = new ArrayList<>(savedSteps);
            ticket.getSteps().clear();
            ticket.getSteps().addAll(copy);
        }
        return anchorSteps;
    }

    private void sendReviewSuccessNotification(Ticket ticket) {
        User creator = ticket.getUser();
        if (creator == null) return;

        String title = "Sự cố của bạn đã được tiếp nhận và xử lý";
        String message = "Phản ánh của bạn đã được quản lý phê duyệt và chuyển cho cán bộ chuyên trách tiến hành xử lý.";

        notificationService.createAndSendNotification(ticket, List.of(creator), title, message);

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

        notificationService.createAndSendNotification(ticket, List.of(creator), title, message);

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
                steps = ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticket.getTicketId());
            }
            if (steps == null || steps.isEmpty()) {
                steps = ticket.getSteps();
            }
            if (steps != null && !steps.isEmpty()) {
                List<TicketStep> cascaded = cascadeSubsequentStepDeadlines(steps, ticketStep, now);
                if (!cascaded.isEmpty()) {
                    ticketStepRepository.saveAll(cascaded);
                }
                if (ticket.getSteps() == null) {
                    ticket.setSteps(new ArrayList<>(steps));
                } else if (ticket.getSteps() != steps) {
                    ticket.getSteps().clear();
                    ticket.getSteps().addAll(steps);
                }
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

        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticketId);
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
                .sorted(Comparator.comparing(TicketStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TicketStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
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

        if (ticket.getSteps() == null) {
            ticket.setSteps(new ArrayList<>(steps));
        } else if (ticket.getSteps() != steps) {
            ticket.getSteps().clear();
            ticket.getSteps().addAll(steps);
        }
        ticket.setDeadline(resolveTicketDeadline(ticket));
        ticketRepository.save(ticket);

        return ticketStepMapper.toTicketStepResponseList(steps);
    }

    private boolean isStepPendingOrActive(TicketStep step) {
        if (step == null || step.isDeleted()) return false;
        AssignmentStatus status = step.getStatus();
        return status != AssignmentStatus.COMPLETED
                && status != AssignmentStatus.RESOLVED
                && status != AssignmentStatus.CANCELLED;
    }

    private List<TicketStep> cascadeSubsequentStepDeadlines(List<TicketStep> steps, TicketStep targetStep, LocalDateTime now) {
        List<TicketStep> sortedSteps = steps.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isDeleted())
                .sorted(Comparator.comparing(TicketStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TicketStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
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
    public TicketStepResponse updateTicketStepDependencies(UUID ticketStepId, UpdateTicketStepDependenciesRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        TicketStep targetStep = ticketStepRepository.findById(ticketStepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        if (targetStep.isDeleted()) {
            throw new AppException(ErrorCode.STEP_NOT_FOUND);
        }

        Ticket ticket = targetStep.getTicket();
        if (ticket == null) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        List<TicketStep> allSteps = ticketStepRepository.findByTicketTicketId(ticket.getTicketId())
                .stream()
                .filter(s -> !s.isDeleted())
                .toList();

        Map<UUID, TicketStep> stepByIdMap = allSteps.stream()
                .collect(Collectors.toMap(TicketStep::getId, Function.identity()));

        List<UUID> requestedDepIds = request.getDependsOnStepIds() != null
                ? request.getDependsOnStepIds()
                : Collections.emptyList();

        // 1. Kiểm tra không tự phụ thuộc
        if (requestedDepIds.contains(ticketStepId)) {
            throw new AppException(ErrorCode.INVALID_STEP_DEPENDENCY);
        }

        // 2. Kiểm tra các bước phụ thuộc phải cùng thuộc một vé
        for (UUID depId : requestedDepIds) {
            if (!stepByIdMap.containsKey(depId)) {
                throw new AppException(ErrorCode.INVALID_STEP_DEPENDENCY);
            }
        }

        // 3. Xây dựng adjacency map mô phỏng sau khi cập nhật
        Set<UUID> allStepIds = stepByIdMap.keySet();
        Map<UUID, Set<UUID>> adjacencyMap = new HashMap<>();
        for (TicketStep s : allSteps) {
            if (s.getId().equals(ticketStepId)) {
                adjacencyMap.put(s.getId(), new HashSet<>(requestedDepIds));
            } else {
                Set<UUID> existingDeps = (s.getDependsOn() != null)
                        ? s.getDependsOn().stream()
                                .filter(dep -> !dep.isDeleted())
                                .map(TicketStep::getId)
                                .collect(Collectors.toSet())
                        : Collections.emptySet();
                adjacencyMap.put(s.getId(), existingDeps);
            }
        }

        // 4. Kiểm tra chu trình và đảm bảo sau khi sửa vẫn còn ít nhất 1 anchor step
        StepDagValidator.validateDag(allStepIds, adjacencyMap);

        // 5. Cập nhật lại trường dependsOn cho stepId
        Set<TicketStep> newDependsOn = requestedDepIds.stream()
                .map(stepByIdMap::get)
                .collect(Collectors.toSet());

        targetStep.setDependsOn(newDependsOn);
        LocalDateTime now = LocalDateTime.now();
        targetStep.setUpdatedAt(now);

        // Nếu vé đang IN_PROGRESS và step đang ASSIGNED mà các điều kiện tiên quyết đã hoàn tất, tự kích hoạt
        if (ticket.getStatus() == TicketStatus.IN_PROGRESS && targetStep.getStatus() == AssignmentStatus.ASSIGNED) {
            boolean allPrerequisitesResolved = newDependsOn.stream()
                    .filter(dep -> !dep.isDeleted() && dep.getStatus() != AssignmentStatus.CANCELLED)
                    .allMatch(dep -> dep.getStatus() == AssignmentStatus.RESOLVED 
                                  || dep.getStatus() == AssignmentStatus.COMPLETED);
            if (allPrerequisitesResolved) {
                targetStep.setStatus(AssignmentStatus.IN_PROGRESS);
                targetStep.setStartedAt(now);
                if (targetStep.getDeadline() == null && targetStep.getStep() != null && targetStep.getStep().getDuration() != null && targetStep.getStep().getDuration() > 0) {
                    targetStep.setDeadline(now.plusHours(targetStep.getStep().getDuration()));
                }
                notificationService.notifyStepStarted(targetStep, currentUser);
            }
        }

        targetStep = ticketStepRepository.save(targetStep);
        return ticketStepMapper.toTicketStepResponse(targetStep);
    }

    @Override
    @Transactional
    @Deprecated(since = "v2.0", forRemoval = false)
    public TicketStepResponse updateTicketStepOrderIndex(UUID ticketStepId, UpdateTicketStepOrderRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        TicketStep ticketStep = ticketStepRepository.findById(ticketStepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        return ticketStepMapper.toTicketStepResponse(ticketStep);
    }

    @Override
    @Transactional
    @Deprecated(since = "v2.0", forRemoval = false)
    public List<TicketStepResponse> reorderTicketSteps(UUID ticketId, ReorderTicketStepsRequest request) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }

        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticketId);
        return ticketStepMapper.toTicketStepResponseList(steps);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getResolvedTickets(Pageable pageable) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Page<Ticket> tickets = ticketRepository.findResolvedTicketsWithAllStepsResolved(pageable);

        return tickets.map(ticket -> {
            TicketResponse response = ticketMapper.toTicketResponse(ticket);
            if (response != null) {
                List<TicketStep> steps = ticket.getSteps();
                if ((steps == null || steps.isEmpty()) && ticket.getTicketId() != null) {
                    steps = ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticket.getTicketId());
                }
                List<TicketStep> allSteps = (steps != null ? steps : Collections.<TicketStep>emptyList())
                        .stream()
                        .filter(s -> s != null && !s.isDeleted())
                        .sorted(Comparator.comparing(TicketStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(TicketStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                response.setSteps(ticketStepMapper.toTicketStepResponseList(allSteps));
            }
            return response;
        });
    }

    @Override
    @Transactional
    public TicketStepResponse updateResolvedTicketStepStatus(UUID stepId, AssignmentStatus targetStatus) {
        User currentUser = getCurrentUser();
        if (!isManager(currentUser)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        TicketStep step = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        if (step.getStatus() != AssignmentStatus.RESOLVED
                || (targetStatus != AssignmentStatus.COMPLETED && targetStatus != AssignmentStatus.IN_PROGRESS)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        LocalDateTime now = LocalDateTime.now();

        if (targetStatus == AssignmentStatus.COMPLETED) {
            step.setStatus(AssignmentStatus.COMPLETED);
            if (step.getCompletedAt() == null) {
                step.setCompletedAt(now);
            }
            step.setUpdatedAt(now);
            step = ticketStepRepository.save(step);

            if (ticketWorkflowEngine != null) {
                ticketWorkflowEngine.handleStepCompletion(stepId);
                step = ticketStepRepository.findById(stepId).orElse(step);
            }
        } else if (targetStatus == AssignmentStatus.IN_PROGRESS) {
            step.setStatus(AssignmentStatus.IN_PROGRESS);
            step.setCompletedAt(null);
            step.setUpdatedAt(now);
            step = ticketStepRepository.save(step);

            Ticket ticket = step.getTicket();
            if (ticket != null && ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                TicketStatus prevStatus = ticket.getStatus();
                ticket.setStatus(TicketStatus.IN_PROGRESS);
                ticketRepository.save(ticket);

                String stepName = (step.getStep() != null && step.getStep().getStepName() != null)
                        ? step.getStep().getStepName()
                        : String.valueOf(step.getId());
                TicketHistory history = ticketHistoryMapper.toTicketHistory(
                        ticket, currentUser, ActionType.WORK_STARTED, prevStatus, TicketStatus.IN_PROGRESS,
                        "Quản lý yêu cầu thực hiện lại bước xử lý '" + stepName + "'. Sự cố chuyển về trạng thái Đang xử lý."
                );
                if (history != null) {
                    ticketHistoryRepository.save(history);
                }
            }
        }

        return ticketStepMapper.toTicketStepResponse(step);
    }
}
