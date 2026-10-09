package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.request.RerouteTicketRequest;
import com.cityfix.backend.model.dto.request.ReviewTicketRequest;
import com.cityfix.backend.model.dto.request.AssignTicketRequest;
import com.cityfix.backend.model.dto.request.RejectTicketRequest;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;

import com.cityfix.backend.repository.projection.DuplicateTicketProjection;

import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.repository.ReportImageRepository;
import com.cityfix.backend.repository.TicketVoteRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.TicketService;
import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketVoteRepository ticketVoteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final ReportImageRepository reportImageRepository;
    private final com.cityfix.backend.repository.RoutingRuleRepository routingRuleRepository;
    private final com.cityfix.backend.repository.DepartmentRepository departmentRepository;
    private final com.cityfix.backend.repository.TicketStepRepository ticketStepRepository;
    private final com.cityfix.backend.repository.TicketSubCategoryRepository ticketSubCategoryRepository;
    private final com.cityfix.backend.mapper.SubCategoryMapper subCategoryMapper;
    private final com.cityfix.backend.repository.StepRepository stepRepository;
    private final com.cityfix.backend.repository.TicketHistoryRepository ticketHistoryRepository;
    private final Cloudinary cloudinary;
    private final ImageMapper imageMapper;
    private final TicketMapper ticketMapper;
    private final com.cityfix.backend.mapper.DepartmentMapper departmentMapper;
    private final com.cityfix.backend.mapper.TicketStepMapper ticketStepMapper;
    private final com.cityfix.backend.mapper.TicketHistoryMapper ticketHistoryMapper;
    private final com.cityfix.backend.repository.NotificationRepository notificationRepository;
    private final com.cityfix.backend.mapper.NotificationMapper notificationMapper;
    private final com.cityfix.backend.service.EmailService emailService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;


    private static final List<String> ALLOWED_IMAGE_MIME_TYPES = List.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif"
    );

    private static final List<String> ALLOWED_VIDEO_MIME_TYPES = List.of(
            "video/mp4",
            "video/quicktime",
            "video/webm",
            "video/x-msvideo",
            "video/3gpp"
    );

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_FILE_COUNT = 7;

    private static final java.util.concurrent.Executor uploadExecutor = java.util.concurrent.Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 8)
    );

    private static final double DUPLICATE_SEARCH_RADIUS_METERS = 50.0;

    // WGS84 SRID is 4326
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private User getCurrentUser() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null 
                || !authentication.isAuthenticated() 
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String email;
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            email = user.getEmail();
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = principal.toString();
        }


        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        User user = getCurrentUser();

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        List<com.cityfix.backend.model.entity.SubCategory> subCategories = new java.util.ArrayList<>();
        if (request.getSubCategoryIds() != null && !request.getSubCategoryIds().isEmpty()) {
            for (UUID subCatId : request.getSubCategoryIds()) {
                com.cityfix.backend.model.entity.SubCategory subCat = subCategoryRepository.findById(subCatId)
                        .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));
                if (!subCat.isActive() || subCat.isDeleted()) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Sự cố phụ đã bị vô hiệu hóa hoặc bị xóa");
                }
                if (category != null && !subCat.getCategory().getCategoryId().equals(category.getCategoryId())) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Sự cố phụ không thuộc danh mục đã chọn");
                }
                subCategories.add(subCat);
            }
        }

        Point location = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        boolean isDangerous = (request.getPriority() == com.cityfix.backend.model.enums.Priority.HIGH || request.getPriority() == com.cityfix.backend.model.enums.Priority.MEDIUM);
        boolean isAiUncertain = (request.getConfidenceScore() != null && request.getConfidenceScore() < 70.0);
        boolean needsReview = isDangerous || isAiUncertain;

        // Auto-Routing Engine lookup
        Department assignedDepartment = null;
        if (category != null) {
            String userProvince = user.getProvince();
            String userWard = user.getWard();

            java.util.Optional<RoutingRule> ruleOpt =
                    routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                            category.getCategoryId(), userProvince, userWard);

            if (ruleOpt.isEmpty() && userProvince != null) {
                ruleOpt = routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                        category.getCategoryId(), userProvince, null);
            }

            if (ruleOpt.isEmpty()) {
                List<RoutingRule> rules =
                        routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(category.getCategoryId());
                if (!rules.isEmpty()) {
                    ruleOpt = java.util.Optional.of(rules.get(0));
                }
            }

            if (ruleOpt.isPresent()) {
                assignedDepartment = ruleOpt.get().getDepartment();
            }
        }

        boolean finalNeedsReview = needsReview || (assignedDepartment == null);
        TicketStatus initialStatus = finalNeedsReview ? TicketStatus.INITIATED : TicketStatus.OPEN;

        Ticket ticket = ticketMapper.toTicket(request, category, user, location, initialStatus, finalNeedsReview);
        ticket = ticketRepository.save(ticket);

        for (com.cityfix.backend.model.entity.SubCategory subCat : subCategories) {
            com.cityfix.backend.model.entity.TicketSubCategory tsc = com.cityfix.backend.model.entity.TicketSubCategory.builder()
                    .ticket(ticket)
                    .subCategory(subCat)
                    .build();
            ticketSubCategoryRepository.save(tsc);
            ticket.getSubCategories().add(tsc);

            List<Step> steps = stepRepository.findBySubCategory_SubCategoryId(subCat.getSubCategoryId());
            int index = ticket.getSteps() != null ? ticket.getSteps().size() : 0;
            for (Step s : steps) {
                TicketStep ts = TicketStep.builder()
                        .ticket(ticket)
                        .step(s)
                        .status(AssignmentStatus.OPEN)
                        .orderIndex(index++)
                        .build();
                ticketStepRepository.save(ts);
                ticket.getSteps().add(ts);
            }
        }

        // Audit Log
        String historyNote;
        if (finalNeedsReview) {
            if (assignedDepartment != null) {
                historyNote = "Chuyển sang duyệt thủ công do tính chất nguy hiểm hoặc điểm AI thấp (Đã dự kiến gợi ý: " + assignedDepartment.getName() + ")";
            } else {
                historyNote = "Chuyển sang duyệt thủ công do chưa có quy tắc định tuyến phù hợp";
            }
        } else {
            historyNote = "Tự động định tuyến thành công tới đơn vị: " + assignedDepartment.getName();
        }

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, user, ActionType.ROUTED, null, initialStatus, historyNote
        );
        ticketHistoryRepository.save(history);

        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            ReportImage image = imageMapper.toReportImage(ticket, request.getImageUrl());
            reportImageRepository.save(image);
        }

        TicketResponse response = ticketMapper.toTicketResponse(ticket);
        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            response.setImageUrl(request.getImageUrl());
        }
        return response;
    }

    @Override
    @Transactional
    public void upvoteTicket(UUID ticketId) {
        User user = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        if (ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, user.getUserId())) {
            throw new RuntimeException("User has already upvoted this ticket");
        }

        TicketVote vote = TicketVote.builder()
                .ticket(ticket)
                .user(user)
                .build();
        
        ticketVoteRepository.save(vote);
    }

    @Override
    public List<ImageUploadResponse> uploadImage(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        if (files.size() > MAX_FILE_COUNT) {
            throw new AppException(ErrorCode.FILE_COUNT_EXCEEDED);
        }

        for (MultipartFile file : files) {
            validateFile(file);
        }

        String targetFolder = (folder != null && !folder.isBlank()) ? folder : "cityfix/reports";

        List<java.util.concurrent.CompletableFuture<ImageUploadResponse>> futures = files.stream()
                .map(file -> java.util.concurrent.CompletableFuture.supplyAsync(() -> uploadSingleFile(file, targetFolder), uploadExecutor))
                .toList();

        try {
            java.util.concurrent.CompletableFuture<Void> allOf = java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]));
            allOf.join();
            return futures.stream().map(java.util.concurrent.CompletableFuture::join).toList();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AppException appException) {
                throw appException;
            }
            log.error("Lỗi khi upload tệp lên Cloudinary: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String lowerContentType = contentType.toLowerCase();
        boolean isImage = ALLOWED_IMAGE_MIME_TYPES.contains(lowerContentType);
        boolean isVideo = ALLOWED_VIDEO_MIME_TYPES.contains(lowerContentType);

        if (!isImage && !isVideo) {
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        if (isImage && file.getSize() > MAX_IMAGE_SIZE) {
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        if (isVideo && file.getSize() > MAX_VIDEO_SIZE) {
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private ImageUploadResponse uploadSingleFile(MultipartFile file, String targetFolder) {
        try {
            Map<String, Object> uploadParams = Map.of(
                    "folder", targetFolder,
                    "resource_type", "auto",
                    "unique_filename", true
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            return imageMapper.toImageUploadResponse(uploadResult);
        } catch (IOException e) {
            log.error("Lỗi khi upload tệp lên Cloudinary: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DuplicateTicketResponse> findDuplicateTickets(Double latitude, Double longitude, UUID categoryId) {
        if (latitude == null || longitude == null || categoryId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        List<DuplicateTicketProjection> projections = ticketRepository.findDuplicateTickets(latitude, longitude, categoryId);
        return ticketMapper.toDuplicateTicketResponseList(projections);
    }
    
    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponse> getInitiatedTickets(org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Ticket> tickets = ticketRepository.findByStatus(TicketStatus.INITIATED, pageable);
        return tickets.map(ticketMapper::toTicketResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getSuggestedDepartments(UUID ticketId, UUID categoryId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));
        
        UUID searchCategoryId = categoryId != null ? categoryId : (ticket.getCategory() != null ? ticket.getCategory().getCategoryId() : null);
        if (searchCategoryId == null) {
            return java.util.Collections.emptyList();
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

    private void validateStaffDepartmentAccess(Ticket ticket, User currentUser) {
        boolean isAdmin = currentUser.getRole() != null && "ADMIN".equalsIgnoreCase(currentUser.getRole().getRoleName());
        if (isAdmin) return;

        boolean isStaff = currentUser.getRole() != null && "STAFF".equalsIgnoreCase(currentUser.getRole().getRoleName());
        if (!isStaff) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (currentUser.getDepartment() == null) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        UUID userDeptId = currentUser.getDepartment().getDepartmentId();

        boolean hasSteps = ticket.getSteps() != null && !ticket.getSteps().isEmpty();
        if (hasSteps) {
            boolean belongsToDept = ticket.getSteps().stream()
                    .anyMatch(step -> step.getAssignee() != null 
                            && step.getAssignee().getDepartment() != null 
                            && userDeptId.equals(step.getAssignee().getDepartment().getDepartmentId()));

            if (!belongsToDept) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }
    }

    @Override
    @Transactional
    public TicketResponse reviewTicket(UUID ticketId, ReviewTicketRequest request) {
        User currentUser = getCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));
                
        validateStaffDepartmentAccess(ticket, currentUser);

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
        
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setNeedsManualReview(false);
        
        if (request.getDepartmentIds() != null && !request.getDepartmentIds().isEmpty()) {
            if (ticket.getSubCategories() != null && !ticket.getSubCategories().isEmpty()) {
                for (com.cityfix.backend.model.entity.TicketSubCategory tsc : ticket.getSubCategories()) {
                    if (tsc.getSubCategory() != null) {
                        List<Step> steps = stepRepository.findBySubCategory_SubCategoryId(tsc.getSubCategory().getSubCategoryId());
                        int index = ticket.getSteps() != null ? ticket.getSteps().size() : 0;
                        for (Step s : steps) {
                            TicketStep ts = TicketStep.builder()
                                    .ticket(ticket)
                                    .step(s)
                                    .status(AssignmentStatus.OPEN)
                                    .orderIndex(index++)
                                    .build();
                            ticketStepRepository.save(ts);
                            ticket.getSteps().add(ts);
                        }
                    }
                }
            }
        }
        
        ticket = ticketRepository.save(ticket);
        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse rerouteTicket(UUID ticketId, RerouteTicketRequest request) {
        User currentUser = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffDepartmentAccess(ticket, currentUser);

        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new AppException(ErrorCode.TICKET_INVALID_STATUS);
        }

        Department targetDepartment = departmentRepository.findById(request.getTargetDepartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));

        // Mark old active steps as TRANSFERRED
        List<TicketStep> activeSteps =
                ticketStepRepository.findByTicketTicketIdAndStatusIn(
                        ticketId,
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OPEN, AssignmentStatus.IN_PROGRESS)
                );

        for (TicketStep oldStep : activeSteps) {
            oldStep.setStatus(AssignmentStatus.TRANSFERRED);
            ticketStepRepository.save(oldStep);
        }

        TicketStatus prevStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setNeedsManualReview(false);
        ticket = ticketRepository.save(ticket);

        // Audit Log
        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REROUTED, prevStatus, TicketStatus.ASSIGNED, request.getReason()
        );
        ticketHistoryRepository.save(history);

        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponse> getAssignedTicketsForStaff(
            AssignmentStatus status, org.springframework.data.domain.Pageable pageable) {
        User currentUser = getCurrentUser();

        if (currentUser.getDepartment() == null) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        UUID deptId = currentUser.getDepartment().getDepartmentId();

        List<AssignmentStatus> targetStatuses = (status != null)
                ? List.of(status)
                : List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OPEN, AssignmentStatus.IN_PROGRESS);

        org.springframework.data.domain.Page<Ticket> ticketsPage =
                ticketStepRepository.findDistinctTicketsByDepartmentAndStatusIn(deptId, targetStatuses, pageable);

        return ticketsPage.map(ticketMapper::toTicketResponse);
    }

    @Override
    @Transactional
    public TicketResponse assignTicket(UUID ticketId, AssignTicketRequest request) {
        User currentUser = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffDepartmentAccess(ticket, currentUser);

        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new AppException(ErrorCode.TICKET_INVALID_STATUS);
        }

        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdAndStatusIn(
                ticketId, List.of(AssignmentStatus.OPEN, AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS));
                
        TicketStep stepToAssign = steps.stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST));

        User assignee = userRepository.findById(request.getAssigneeId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        stepToAssign.setAssignee(assignee);
        stepToAssign.setStatus(AssignmentStatus.ASSIGNED);
        ticketStepRepository.save(stepToAssign);

        TicketStatus prevStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket = ticketRepository.save(ticket);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.ASSIGNED, prevStatus, TicketStatus.ASSIGNED, "Phân công cho " + assignee.getFullName()
        );
        ticketHistoryRepository.save(history);

        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse forwardTicket(UUID ticketId, RejectTicketRequest request) {
        User currentUser = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffDepartmentAccess(ticket, currentUser);

        if (request.getSuggestedCategoryId() != null) {
            Category category = categoryRepository.findById(request.getSuggestedCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            ticket.setCategory(category);
            ticket = ticketRepository.save(ticket);
        }

        TicketHistory forwardHistory = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REROUTED, ticket.getStatus(), ticket.getStatus(), request.getReason()
        );
        ticketHistoryRepository.save(forwardHistory);

        UUID targetDepartmentId = request.getSuggestedDepartmentId();
        if (targetDepartmentId == null && request.getSuggestedCategoryId() != null) {
            List<DepartmentResponse> suggestedDepts = getSuggestedDepartments(ticketId, request.getSuggestedCategoryId());
            if (suggestedDepts != null && !suggestedDepts.isEmpty()) {
                targetDepartmentId = suggestedDepts.get(0).departmentId();
            }
        }
        
        if (targetDepartmentId == null) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }

        RerouteTicketRequest rerouteReq = RerouteTicketRequest.builder()
                .targetDepartmentId(targetDepartmentId)
                .reason(request.getReason() != null ? request.getReason() : "Từ chối và định tuyến lại")
                .build();
                
        return this.rerouteTicket(ticketId, rerouteReq);
    }

    @Override
    @Transactional
    public TicketResponse rejectInitiatedTicket(UUID ticketId, String reason) {
        User currentUser = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffDepartmentAccess(ticket, currentUser);

        if (ticket.getStatus() != TicketStatus.INITIATED) {
            throw new AppException(ErrorCode.TICKET_NOT_INITIATED);
        }

        String finalReason = (reason == null || reason.trim().isEmpty()) ? "Không có lý do từ chối" : reason;

        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setNeedsManualReview(false);
        ticket.setReviewNote(finalReason);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REJECTED, TicketStatus.INITIATED, TicketStatus.REJECTED, finalReason
        );
        ticketHistoryRepository.save(history);

        ticket = ticketRepository.save(ticket);
        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse cancelTicket(UUID ticketId) {
        User currentUser = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getUser() == null || !ticket.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (ticket.getStatus() != TicketStatus.INITIATED) {
            throw new AppException(ErrorCode.TICKET_INVALID_STATUS);
        }

        ticket.setStatus(TicketStatus.CANCELLED);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.CANCELLED, TicketStatus.INITIATED, TicketStatus.CANCELLED, "Người dân tự hủy vé"
        );
        ticketHistoryRepository.save(history);

        ticket = ticketRepository.save(ticket);
        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse rejectAssignedTicket(UUID ticketId, String reason) {
        User currentUser = getCurrentUser();

        boolean isStaff = currentUser.getRole() != null && "STAFF".equalsIgnoreCase(currentUser.getRole().getRoleName());
        if (!isStaff) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        if (ticket.getStatus() != TicketStatus.ASSIGNED && ticket.getStatus() != TicketStatus.OPEN) {
            throw new AppException(ErrorCode.TICKET_INVALID_STATUS);
        }

        String finalReason = (reason == null || reason.trim().isEmpty()) ? "Không có lý do từ chối" : reason;

        TicketStatus prevStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setReviewNote(finalReason);

        // Cancel active steps
        List<TicketStep> activeSteps =
                ticketStepRepository.findByTicketTicketIdAndStatusIn(
                        ticketId,
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OPEN, AssignmentStatus.IN_PROGRESS)
                );

        for (TicketStep step : activeSteps) {
            step.setStatus(AssignmentStatus.CANCELLED);
            ticketStepRepository.save(step);
        }

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REJECTED, prevStatus, TicketStatus.REJECTED, finalReason
        );
        ticketHistoryRepository.save(history);

        ticket = ticketRepository.save(ticket);
        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.cityfix.backend.model.dto.response.TicketHistoryResponse> getTicketHistory(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }
        List<TicketHistory> histories = ticketHistoryRepository.findByTicketTicketIdOrderByCreatedAtDesc(ticketId);
        return ticketHistoryMapper.toTicketHistoryResponseList(histories);
    }
}




