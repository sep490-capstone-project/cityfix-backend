package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.EvidenceMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.model.dto.request.UpdateStaffTicketStatusRequest;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.EmailService;
import com.cityfix.backend.service.StaffTicketService;
import com.cityfix.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffTicketServiceImpl implements StaffTicketService {

    private final TicketRepository ticketRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final ResolutionEvidenceRepository resolutionEvidenceRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private final TicketMapper ticketMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final EvidenceMapper evidenceMapper;
    private final NotificationMapper notificationMapper;

    private final TicketService ticketService;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    private static final java.util.Map<TicketStatus, java.util.Set<TicketStatus>> ALLOWED_TRANSITIONS = java.util.Map.of(
            TicketStatus.ASSIGNED, java.util.Set.of(TicketStatus.IN_PROGRESS, TicketStatus.REJECTED),
            TicketStatus.IN_PROGRESS, java.util.Set.of(TicketStatus.RESOLVED, TicketStatus.COMPLETED, TicketStatus.CANCELLED),
            TicketStatus.RESOLVED, java.util.Set.of(TicketStatus.COMPLETED)
    );

    private void validateStatusTransition(TicketStatus currentStatus, TicketStatus targetStatus, boolean isAdmin) {
        if (currentStatus == targetStatus) return;
        if (isAdmin) return; // Admin can jump states if needed, or keeping it strict for everyone.
        
        java.util.Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, java.util.Collections.emptySet());
        if (!allowed.contains(targetStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateStaffAccess(Ticket ticket, User currentUser, boolean isAdmin) {
        if (isAdmin) return;

        boolean isStaff = currentUser.getRole() != null && "STAFF".equalsIgnoreCase(currentUser.getRole().getRoleName());
        if (!isStaff) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (currentUser.getDepartment() == null) {
            throw new AppException(ErrorCode.USER_NOT_IN_DEPARTMENT);
        }

        UUID userDeptId = currentUser.getDepartment().getDepartmentId();

        boolean hasActiveStepInDept = ticket.getSteps() != null && ticket.getSteps().stream()
                .anyMatch(s -> (s.getStatus() == AssignmentStatus.IN_PROGRESS || s.getStatus() == AssignmentStatus.ASSIGNED || s.getStatus() == AssignmentStatus.OPEN)
                        && s.getAssignee() != null 
                        && s.getAssignee().getDepartment() != null 
                        && userDeptId.equals(s.getAssignee().getDepartment().getDepartmentId()));

        if (!hasActiveStepInDept) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public TicketResponse updateTicketStatus(UUID ticketId, TicketStatus targetStatus, UpdateStaffTicketStatusRequest request) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() != null && "ADMIN".equalsIgnoreCase(currentUser.getRole().getRoleName());

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffAccess(ticket, currentUser, isAdmin);

        TicketStatus prevStatus = ticket.getStatus();
        
        validateStatusTransition(prevStatus, targetStatus, isAdmin);

        if (targetStatus == TicketStatus.COMPLETED) {
            if (!resolutionEvidenceRepository.existsByTicket_TicketId(ticketId)) {
                throw new AppException(ErrorCode.EVIDENCE_REQUIRED);
            }
        }

        ticket.setStatus(targetStatus);

        List<TicketStep> activeSteps = ticketStepRepository.findByTicketTicketIdAndStatusInOrderByOrderIndexAsc(
                ticketId, List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OPEN, AssignmentStatus.IN_PROGRESS)
        );

        if (!activeSteps.isEmpty()) {
            if (targetStatus == TicketStatus.IN_PROGRESS) {
                TicketStep firstStep = activeSteps.get(0);
                firstStep.setStatus(AssignmentStatus.IN_PROGRESS);
                if (firstStep.getStartedAt() == null) {
                    firstStep.setStartedAt(java.time.LocalDateTime.now());
                }
                ticketStepRepository.save(firstStep);
            } else if (targetStatus == TicketStatus.COMPLETED || targetStatus == TicketStatus.RESOLVED || targetStatus == TicketStatus.CANCELLED) {
                AssignmentStatus targetStepStatus = (targetStatus == TicketStatus.CANCELLED) ? AssignmentStatus.CANCELLED : AssignmentStatus.COMPLETED;
                for (TicketStep step : activeSteps) {
                    step.setStatus(targetStepStatus);
                    step.setCompletedAt(java.time.LocalDateTime.now());
                }
                ticketStepRepository.saveAll(activeSteps);
            }
        }

        ticket = ticketRepository.save(ticket);

        String note = (request != null && request.getNote() != null) ? request.getNote() : "Cập nhật trạng thái vé sang " + targetStatus;
        
        ActionType action = ActionType.WORK_STARTED;
        if (targetStatus == TicketStatus.RESOLVED || targetStatus == TicketStatus.COMPLETED) {
            action = ActionType.CLOSED;
        } else if (targetStatus == TicketStatus.CANCELLED || targetStatus == TicketStatus.REJECTED) {
            action = ActionType.CANCELLED;
        }

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, action, prevStatus, targetStatus, note
        );
        ticketHistoryRepository.save(history);

        sendTicketStatusNotification(ticket, targetStatus);

        return ticketMapper.toTicketResponse(ticket);
    }

    private void sendTicketStatusNotification(Ticket ticket, TicketStatus targetStatus) {
        User ticketUser = ticket.getUser();
        if (ticketUser != null) {
            String title;
            String message;

            if (targetStatus == TicketStatus.RESOLVED || targetStatus == TicketStatus.COMPLETED) {
                title = "Sự cố đã được giải quyết";
                message = "Sự cố đã được đơn vị xử lý hoàn tất. Vui lòng kiểm tra và đánh giá kết quả.";
            } else if (targetStatus == TicketStatus.IN_PROGRESS) {
                title = "Cập nhật sự cố: Đang xử lý";
                message = "Sự cố của bạn đang được cán bộ chuyên trách tiến hành xử lý.";
            } else if (targetStatus == TicketStatus.CANCELLED || targetStatus == TicketStatus.REJECTED) {
                title = "Sự cố đã bị hủy hoặc từ chối";
                message = "Sự cố của bạn đã bị hủy hoặc từ chối. Vui lòng xem chi tiết trên hệ thống.";
            } else {
                return;
            }

            Notification notification = Notification.builder()
                    .user(ticketUser)
                    .title(title)
                    .content(message)
                    .isRead(false)
                    .build();
            notification = notificationRepository.save(notification);

            try {
                com.cityfix.backend.model.dto.response.NotificationResponse notifResponse = notificationMapper.toResponse(notification);
                messagingTemplate.convertAndSend("/topic/notifications/" + ticketUser.getUserId(), notifResponse);
            } catch (Exception e) {
                log.error("Lỗi khi gửi websocket notification: {}", e.getMessage());
            }

            if (ticketUser.getEmail() != null) {
                emailService.sendTicketStatusEmail(ticketUser.getEmail(), title, message);
            }
        }
    }

    @Override
    @Transactional
    public List<ResolutionEvidenceResponse> submitEvidence(UUID ticketId, List<MultipartFile> files, String resolutionNote) {
        User currentUser = getCurrentUser();
        boolean isAdmin = currentUser.getRole() != null && "ADMIN".equalsIgnoreCase(currentUser.getRole().getRoleName());

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        validateStaffAccess(ticket, currentUser, isAdmin);

        List<ImageUploadResponse> uploadResponses = ticketService.uploadImage(files, "cityfix/evidences");

        List<ResolutionEvidence> evidences = uploadResponses.stream().map(res -> {
            ResolutionEvidence evidence = ResolutionEvidence.builder()
                    .ticket(ticket)
                    .staff(currentUser)
                    .imageUrl(res.getSecureUrl())
                    .resolutionNote(resolutionNote)
                    .build();
            return evidence;
        }).collect(Collectors.toList());

        evidences = resolutionEvidenceRepository.saveAll(evidences);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.EVIDENCE_SUBMITTED, ticket.getStatus(), ticket.getStatus(), "Đã tải lên bằng chứng xử lý"
        );
        ticketHistoryRepository.save(history);

        return evidenceMapper.toResponseList(evidences);
    }
}
