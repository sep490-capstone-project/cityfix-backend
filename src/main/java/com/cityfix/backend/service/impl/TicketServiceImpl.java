package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.mapper.SubCategoryMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.*;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.LocationBoundary;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import com.cityfix.backend.service.EmailService;
import com.cityfix.backend.service.LocationService;
import com.cityfix.backend.service.NotificationService;
import com.cityfix.backend.service.TicketService;
import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.cityfix.backend.util.SecurityUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    private final RoutingRuleRepository routingRuleRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketSubCategoryRepository ticketSubCategoryRepository;
    private final SubCategoryMapper subCategoryMapper;
    private final StepRepository stepRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final Cloudinary cloudinary;
    private final ImageMapper imageMapper;
    private final TicketMapper ticketMapper;
    private final LocationService locationService;
    private final TicketStepMapper ticketStepMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

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

    private static final Executor uploadExecutor = Executors.newFixedThreadPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 8)
    );

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .or(SecurityUtils::getCurrentUserPrincipal)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private boolean isStaff(User user) {
        return user != null && user.getRole() != null && "STAFF".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private boolean isManager(User user) {
        return user != null && user.getRole() != null && "MANAGER".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private boolean isManagerOrStaff(User user) {
        if (user == null || user.getRole() == null) return false;
        String role = user.getRole().getRoleName();
        return "MANAGER".equalsIgnoreCase(role) || "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }

    private void validateStaffDepartmentAccess(Ticket ticket, User currentUser) {
        if (isAdmin(currentUser)) return;

        if (!isStaff(currentUser)) {
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

    private void createOrUpdateTicketStep(Ticket ticket, Step step, User assignee, AssignmentStatus status) {
        createOrUpdateTicketStep(ticket, step, assignee, status, null, null);
    }

    private void createOrUpdateTicketStep(Ticket ticket, Step step, User assignee, AssignmentStatus status, LocalDateTime deadline) {
        createOrUpdateTicketStep(ticket, step, assignee, status, null, deadline);
    }

    private void createOrUpdateTicketStep(Ticket ticket, Step step, User assignee, AssignmentStatus status, LocalDateTime startedAt, LocalDateTime deadline) {
        if (ticket.getSteps() == null) {
            ticket.setSteps(new ArrayList<>());
        }
        TicketStep existingStep = ticket.getSteps().stream()
                .filter(ts -> ts.getStep() != null && step.getStepId().equals(ts.getStep().getStepId()))
                .findFirst()
                .orElse(null);

        if (existingStep != null) {
            existingStep.setAssignee(assignee);
            existingStep.setStatus(status);
            if (startedAt != null) {
                existingStep.setStartedAt(startedAt);
            }
            if (deadline != null) {
                existingStep.setDeadline(deadline);
            }
            ticketStepRepository.save(existingStep);
        } else {
            TicketStep ts = ticketStepMapper.toTicketStepWithTimeline(ticket, step, assignee, status, startedAt, deadline, null);
            if (ts == null) {
                ts = ticketStepMapper.toTicketStep(ticket, step, assignee, status);
            }
            if (ts == null) {
                ts = TicketStep.builder()
                        .ticket(ticket)
                        .step(step)
                        .assignee(assignee)
                        .status(status)
                        .startedAt(startedAt)
                        .deadline(deadline)
                        .build();
            } else {
                if (startedAt != null) {
                    ts.setStartedAt(startedAt);
                }
                if (deadline != null) {
                    ts.setDeadline(deadline);
                }
            }
            ticketStepRepository.save(ts);
            ticket.getSteps().add(ts);
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

    /**
     * Bóc tách an toàn Province hoặc Ward từ chuỗi địa chỉ sự cố theo chuẩn Việt Nam.
     * Chuỗi địa chỉ chuẩn: "Số nhà, Đường, Phường/Xã, Quận/Huyện, Tỉnh/Thành phố"
     *
     * @param address Chuỗi địa chỉ sự cố từ request
     * @param type    "PROVINCE" hoặc "WARD"
     * @return Tên địa phương tương ứng hoặc null nếu không thể bóc tách
     */
    private String extractLocationFromAddress(String address, String type) {
        if (address == null || address.isBlank()) {
            return null;
        }

        String[] parts = address.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        int length = parts.length;
        if (length == 0) {
            return null;
        }

        if ("PROVINCE".equalsIgnoreCase(type)) {
            // Theo chuẩn Việt Nam: Tỉnh/Thành phố luôn nằm ở thành phần cuối cùng
            String province = parts[length - 1];
            return province.isEmpty() ? null : province;
        } else if ("WARD".equalsIgnoreCase(type)) {
            // Chuẩn 4-5 thành phần: "..., Phường/Xã, Quận/Huyện, Tỉnh/Thành phố" -> (length - 3)
            if (length >= 3) {
                String ward = parts[length - 3];
                return ward.isEmpty() ? null : ward;
            } else if (length == 2) {
                // Rút gọn: "Phường/Xã, Tỉnh/Thành phố" -> parts[0]
                String ward = parts[0];
                return ward.isEmpty() ? null : ward;
            }
        }

        return null;
    }

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        User user = getCurrentUser();

        List<SubCategory> subCategories = new ArrayList<>();
        if (request.getSubCategoryIds() != null && !request.getSubCategoryIds().isEmpty()) {
            Set<UUID> seenSubCatIds = new HashSet<>();
            for (UUID subCatId : request.getSubCategoryIds()) {
                if (subCatId == null) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Mã sự cố phụ không được để trống");
                }
                if (!seenSubCatIds.add(subCatId)) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Duplicate subcategory is not allowed");
                }
            }
            for (UUID subCatId : request.getSubCategoryIds()) {
                SubCategory subCat = subCategoryRepository.findById(subCatId)
                        .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));
                if (!subCat.isActive() || subCat.isDeleted()) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Sự cố phụ đã bị vô hiệu hóa hoặc bị xóa");
                }
                subCategories.add(subCat);
            }
        }

        Point location = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        boolean isDangerous = (request.getPriority() == com.cityfix.backend.model.enums.Priority.HIGH || request.getPriority() == com.cityfix.backend.model.enums.Priority.MEDIUM);
        boolean isAiUncertain = (request.getConfidenceScore() != null && request.getConfidenceScore() < 70.0);
        boolean needsReview = isDangerous || isAiUncertain;
        boolean isMultiSubCategory = subCategories.size() > 1;
        boolean finalNeedsReview = needsReview || isMultiSubCategory;

        // Auto-Routing Engine lookup: Ưu tiên Tọa độ GPS (PostGIS) -> Fallback Address
        Department assignedDepartment = null;
        User autoStaff = null;

        if (!finalNeedsReview && !subCategories.isEmpty()) {
            Category singleCategory = subCategories.get(0).getCategory();
            if (singleCategory != null) {
                String province = null;
                String ward = null;

                // Ưu tiên 1: Tra cứu địa giới hành chính bằng PostGIS từ tọa độ GPS
                if (request.getLatitude() != null && request.getLongitude() != null) {
                    LocationBoundary boundary = locationService.getBoundaryFromCoordinates(
                            request.getLatitude(), request.getLongitude()
                    );
                    if (boundary != null) {
                        province = boundary.getProvince();
                        ward = boundary.getWard();
                    }
                }

                // Ưu tiên 2 (Fallback): Bóc tách từ chuỗi Address nếu chưa xác định được từ GPS
                if (province == null || ward == null) {
                    if (province == null) {
                        province = extractLocationFromAddress(request.getAddress(), "PROVINCE");
                    }
                    if (ward == null) {
                        ward = extractLocationFromAddress(request.getAddress(), "WARD");
                    }
                }

                Optional<RoutingRule> ruleOpt = Optional.empty();
                if (province != null && ward != null) {
                    ruleOpt = routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                            singleCategory.getCategoryId(), province, ward);
                }

                if (ruleOpt.isEmpty() && province != null) {
                    ruleOpt = routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(
                            singleCategory.getCategoryId(), province, null);
                }

                if (ruleOpt.isPresent()) {
                    assignedDepartment = ruleOpt.get().getDepartment();
                }
            }

            if (assignedDepartment != null) {
                autoStaff = userRepository.findRepresentativeStaffByDepartmentId(assignedDepartment.getDepartmentId()).orElse(null);
            }

            // Nếu không tìm thấy department hoặc không tìm thấy staff -> chuyển sang duyệt thủ công
            if (assignedDepartment == null || autoStaff == null) {
                finalNeedsReview = true;
            }
        }

        TicketStatus initialStatus = finalNeedsReview ? TicketStatus.INITIATED : TicketStatus.ASSIGNED;

        Ticket ticket = ticketMapper.toTicket(request, user, location, initialStatus, finalNeedsReview);
        if (request.getIsAnonymous() != null) {
            ticket.setIsAnonymous(request.getIsAnonymous());
        }
        ticket = ticketRepository.save(ticket);

        LocalDateTime currentStart = ticket.getCreatedAt() != null ? ticket.getCreatedAt() : LocalDateTime.now();
        for (SubCategory subCat : subCategories) {
            TicketSubCategory tsc = TicketSubCategory.builder()
                    .ticket(ticket)
                    .subCategory(subCat)
                    .build();
            ticketSubCategoryRepository.save(tsc);
            ticket.getSubCategories().add(tsc);

            // Persist corresponding TicketStep rows with autoStaff
            if (!finalNeedsReview && autoStaff != null) {
                List<Step> steps = stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCat.getSubCategoryId());
                for (Step s : steps) {
                    LocalDateTime stepStart = currentStart;
                    LocalDateTime stepDeadline = null;
                    if (s.getDuration() != null && s.getDuration() > 0) {
                        stepDeadline = stepStart.plusHours(s.getDuration());
                        currentStart = stepDeadline;
                    }
                    createOrUpdateTicketStep(ticket, s, autoStaff, AssignmentStatus.ASSIGNED, stepStart, stepDeadline);
                }
            }
        }

        // Calculate and set deadline
        ticket.setDeadline(calculateTicketDeadline(ticket));

        // Audit Log
        String historyNote;
        if (finalNeedsReview) {
            if (isMultiSubCategory) {
                historyNote = "Chuyển sang duyệt thủ công do sự cố có nhiều danh mục chi tiết";
            } else if (assignedDepartment != null) {
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

        List<String> finalImageUrls = new ArrayList<>();
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            request.getImageUrls().stream()
                    .filter(url -> url != null && !url.isBlank())
                    .forEach(finalImageUrls::add);
        } else if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            finalImageUrls.add(request.getImageUrl().trim());
        }

        for (String imgUrl : finalImageUrls) {
            ReportImage reportImage = imageMapper.toReportImage(ticket, imgUrl);
            if (reportImage.getTicket() == null) {
                reportImage.setTicket(ticket);
            }
            ticket.getImages().add(reportImage);
            reportImageRepository.save(reportImage);
        }

        String firstImageUrl = finalImageUrls.isEmpty() ? null : finalImageUrls.get(0);
        ticket.setImageUrl(firstImageUrl);

        // Recurring Ticket Alert Engine (Non-blocking)
        if (request.getLatitude() != null && request.getLongitude() != null && request.getSubCategoryIds() != null && !request.getSubCategoryIds().isEmpty()) {
            try {
                long completedCount = ticketRepository.countRecurringCompletedTickets(
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getSubCategoryIds(),
                        ticket.getTicketId()
                );
                if (completedCount >= 2) {
                    notificationService.notifyRecurringTicketAlert(ticket, completedCount + 1);
                }
            } catch (Exception e) {
                log.error("Lỗi khi kiểm tra cảnh báo sự cố tái diễn cho ticket {}: {}", ticket.getTicketId(), e.getMessage(), e);
            }
        }

        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public void upvoteTicket(UUID ticketId) {
        User user = getCurrentUser();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

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

        List<CompletableFuture<ImageUploadResponse>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> uploadSingleFile(file, targetFolder), uploadExecutor))
                .toList();

        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.join();
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (CompletionException e) {
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
    public List<DuplicateTicketResponse> findDuplicateTickets(Double latitude, Double longitude, UUID subCategoryId) {
        if (latitude == null || longitude == null || subCategoryId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
        List<DuplicateTicketProjection> projections = ticketRepository.findDuplicateTickets(latitude, longitude, subCategoryId);
        return ticketMapper.toDuplicateTicketResponseList(projections);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getInitiatedTickets(Pageable pageable) {
        Page<Ticket> tickets = ticketRepository.findByStatus(TicketStatus.INITIATED, pageable);
        return tickets.map(ticketMapper::toTicketResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyReportedTickets(Pageable pageable) {
        User currentUser = getCurrentUser();
        Page<Ticket> tickets = ticketRepository.findByUserId(currentUser.getUserId(), pageable);
        return tickets.map(ticketMapper::toTicketResponse);
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

        User targetStaff = userRepository.findRepresentativeStaffByDepartmentId(targetDepartment.getDepartmentId())
                .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        // Reset old active steps to ASSIGNED before re-assigning
        List<TicketStep> activeSteps =
                ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(
                        ticketId,
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS)
                );

        for (TicketStep oldStep : activeSteps) {
            oldStep.setStatus(AssignmentStatus.ASSIGNED);
            ticketStepRepository.save(oldStep);
        }

        if (ticket.getSubCategories() != null && !ticket.getSubCategories().isEmpty()) {
            LocalDateTime currentStart = LocalDateTime.now();
            for (TicketSubCategory tsc : ticket.getSubCategories()) {
                if (tsc.getSubCategory() != null) {
                    List<Step> targetSteps = stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(tsc.getSubCategory().getSubCategoryId());
                    for (Step s : targetSteps) {
                        LocalDateTime stepStart = currentStart;
                        LocalDateTime stepDeadline = null;
                        if (s.getDuration() != null && s.getDuration() > 0) {
                            stepDeadline = stepStart.plusHours(s.getDuration());
                            currentStart = stepDeadline;
                        }
                        createOrUpdateTicketStep(ticket, s, targetStaff, AssignmentStatus.ASSIGNED, stepStart, stepDeadline);
                    }
                }
            }
        }

        TicketStatus prevStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setNeedsManualReview(false);
        ticket.setDeadline(calculateTicketDeadline(ticket));
        ticket = ticketRepository.save(ticket);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REROUTED, prevStatus, TicketStatus.ASSIGNED, request.getReason()
        );
        ticketHistoryRepository.save(history);

        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getAssignedTicketsForStaff(
            AssignmentStatus status, Pageable pageable) {
        User currentUser = getCurrentUser();

        if (currentUser.getDepartment() == null) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        List<AssignmentStatus> targetStatuses = (status != null)
                ? List.of(status)
                : List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS, AssignmentStatus.RESOLVED);

        Pageable unsortedPageable = (pageable == null || pageable.isUnpaged())
                ? Pageable.unpaged()
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted());

        Page<Ticket> ticketsPage =
                ticketStepRepository.findDistinctTicketsByAssigneeUserIdAndStatusIn(
                        currentUser.getUserId(), targetStatuses, unsortedPageable
                );

        return ticketsPage.map(ticket -> {
            TicketResponse response = ticketMapper.toTicketResponse(ticket);
            if (response != null) {
                List<TicketStep> staffSteps = (ticket.getSteps() != null ? ticket.getSteps() : Collections.<TicketStep>emptyList())
                        .stream()
                        .filter(s -> s != null && !s.isDeleted())
                        .filter(s -> s.getAssignee() != null && currentUser.getUserId().equals(s.getAssignee().getUserId()))
                        .filter(s -> targetStatuses.contains(s.getStatus()))
                        .sorted(Comparator.comparing(TicketStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(TicketStep::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                response.setSteps(ticketStepMapper.toTicketStepResponseList(staffSteps));
            }
            return response;
        });
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

        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(
                ticketId, List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS));

        TicketStep stepToAssign;
        if (request.getStepId() != null) {
            stepToAssign = steps.stream()
                    .filter(s -> s.getId() != null && s.getId().equals(request.getStepId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));
        } else {
            stepToAssign = steps.stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST));
        }

        User assignee = userRepository.findById(request.getAssigneeId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (assignee.getDepartment() == null) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        if (stepToAssign.getAssignee() != null && stepToAssign.getAssignee().getDepartment() != null) {
            if (!assignee.getDepartment().getDepartmentId().equals(stepToAssign.getAssignee().getDepartment().getDepartmentId())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }
        if (!isAdmin(currentUser)) {
            if (currentUser.getDepartment() == null || !assignee.getDepartment().getDepartmentId().equals(currentUser.getDepartment().getDepartmentId())) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }

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
            categoryRepository.findById(request.getSuggestedCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        TicketHistory forwardHistory = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.REROUTED, ticket.getStatus(), ticket.getStatus(), request.getReason()
        );
        ticketHistoryRepository.save(forwardHistory);

        UUID targetDepartmentId = request.getSuggestedDepartmentId();
        if (targetDepartmentId == null && request.getSuggestedCategoryId() != null) {
            List<RoutingRule> rules = routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(request.getSuggestedCategoryId());
            if (rules != null && rules.size() == 1 && rules.get(0).getDepartment() != null) {
                targetDepartmentId = rules.get(0).getDepartment().getDepartmentId();
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
        cascadeCancelTicketSteps(ticketId);

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

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffDepartmentAccess(ticket, currentUser);

        if (ticket.getStatus() != TicketStatus.ASSIGNED) {
            throw new AppException(ErrorCode.TICKET_INVALID_STATUS);
        }

        String finalReason = (reason == null || reason.trim().isEmpty()) ? "Không có lý do từ chối" : reason;

        TicketStatus prevStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setReviewNote(finalReason);

        cascadeCancelTicketSteps(ticketId);

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
        if (ticket != null && ticket.getTicketId() != null) {
            List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticket.getTicketId());
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
            ticket.setDeadline(calculateTicketDeadline(ticket));
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

        ticket.setSteps(steps);
        ticket.setDeadline(calculateTicketDeadline(ticket));
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
    public LocalDateTime calculateTicketDeadline(Ticket ticket) {
        if (ticket == null) return null;
        List<TicketStep> steps = ticket.getSteps();
        if (steps == null || steps.isEmpty()) {
            if (ticket.getTicketId() != null) {
                steps = ticketStepRepository.findByTicketTicketId(ticket.getTicketId());
            }
        }
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        return steps.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isDeleted())
                .filter(s -> s.getStatus() != AssignmentStatus.CANCELLED)
                .map(TicketStep::getDeadline)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}
