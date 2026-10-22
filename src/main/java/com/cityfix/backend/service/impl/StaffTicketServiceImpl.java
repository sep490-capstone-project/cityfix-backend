package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.EvidenceMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.cityfix.backend.util.SecurityUtils;

import java.time.LocalDateTime;
import java.util.*;
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

    private final TicketMapper ticketMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final EvidenceMapper evidenceMapper;

    private final TicketService ticketService;
    private final EmailService emailService;
    private final com.cityfix.backend.service.NotificationService notificationService;
    private final com.cityfix.backend.service.TicketWorkflowEngine ticketWorkflowEngine;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.ASSIGNED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.REJECTED),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.COMPLETED, TicketStatus.CANCELLED),
            TicketStatus.RESOLVED, Set.of(TicketStatus.COMPLETED)
    );

    private void validateStatusTransition(TicketStatus currentStatus, TicketStatus targetStatus, boolean isAdmin) {
        if (currentStatus == targetStatus) return;
        if (isAdmin) return;
        
        Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowed.contains(targetStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

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

    private boolean isStepAssignedToUserOrDepartment(TicketStep step, User user) {
        if (step == null || step.getAssignee() == null || user == null) {
            return false;
        }
        if (user.getUserId() != null && user.getUserId().equals(step.getAssignee().getUserId())) {
            return true;
        }
        return user.getDepartment() != null 
                && step.getAssignee().getDepartment() != null 
                && user.getDepartment().getDepartmentId().equals(step.getAssignee().getDepartment().getDepartmentId());
    }

    private void validateStepPrerequisites(TicketStep targetStep) {
        if (targetStep == null || targetStep.getDependsOn() == null || targetStep.getDependsOn().isEmpty()) {
            return;
        }
        boolean prerequisitesResolved = targetStep.getDependsOn().stream()
                .filter(dep -> dep != null && !dep.isDeleted() && dep.getStatus() != AssignmentStatus.CANCELLED)
                .allMatch(dep -> dep.getStatus() == AssignmentStatus.RESOLVED 
                              || dep.getStatus() == AssignmentStatus.COMPLETED);
        if (!prerequisitesResolved) {
            throw new AppException(ErrorCode.STEP_PREREQUISITES_NOT_MET);
        }
    }

    private boolean isActiveStatus(AssignmentStatus status) {
        return status == AssignmentStatus.ASSIGNED 
                || status == AssignmentStatus.IN_PROGRESS;
    }

    @Override
    @Transactional
    public TicketResponse updateTicketStatus(UUID ticketId, TicketStatus targetStatus, UpdateStaffTicketStatusRequest request) {
        User currentUser = getCurrentUser();
        boolean admin = isAdmin(currentUser);

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        TicketStatus prevStatus = ticket.getStatus();
        validateStatusTransition(prevStatus, targetStatus, admin);

        List<TicketStep> steps = ticket.getSteps() != null ? ticket.getSteps() : Collections.emptyList();
        UUID requestedStepId = (request != null) ? request.getStepId() : null;

        // 1. Phân biệt rõ giữa "Cập nhật trạng thái của Step" và "Cập nhật trạng thái của Ticket"
        if (requestedStepId != null) {
            TicketStep targetStep = steps.stream()
                    .filter(s -> s.getId() != null && s.getId().equals(requestedStepId))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

            if (!admin && !isStepAssignedToUserOrDepartment(targetStep, currentUser)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }

            if (targetStatus == TicketStatus.RESOLVED || targetStatus == TicketStatus.COMPLETED) {
                validateStepPrerequisites(targetStep);

                if (!resolutionEvidenceRepository.existsByTicketStep_Id(targetStep.getId())) {
                    throw new AppException(ErrorCode.EVIDENCE_REQUIRED);
                }

                targetStep.setStatus(AssignmentStatus.RESOLVED);
                if (targetStep.getCompletedAt() == null) {
                    targetStep.setCompletedAt(LocalDateTime.now());
                }
                targetStep.setUpdatedAt(LocalDateTime.now());
                ticketStepRepository.save(targetStep);

                // Giao quyền quyết định trạng thái ticket cha cho ticketWorkflowEngine
                ticketWorkflowEngine.handleStepCompletion(targetStep.getId());

                // TUYỆT ĐỐI KHÔNG gọi lệnh gán đè ticket.setStatus(targetStatus) hay ticketRepository.save(ticket)
                // Reload lại ticket từ repository để lấy trạng thái mới nhất từ workflow engine
                ticket = ticketRepository.findById(ticketId)
                        .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

                String note = (request != null && request.getNote() != null) ? request.getNote() : "Hoàn tất xử lý bước: " + (targetStep.getStep() != null ? targetStep.getStep().getStepName() : targetStep.getId());
                TicketHistory history = ticketHistoryMapper.toTicketHistory(
                        ticket, currentUser, ActionType.CLOSED, prevStatus, ticket.getStatus(), note
                );
                ticketHistoryRepository.save(history);

                return ticketMapper.toTicketResponse(ticket);
            } else if (targetStatus == TicketStatus.IN_PROGRESS) {
                validateStepPrerequisites(targetStep);

                targetStep.setStatus(AssignmentStatus.IN_PROGRESS);
                if (targetStep.getStartedAt() == null) {
                    targetStep.setStartedAt(LocalDateTime.now());
                }
                targetStep.setUpdatedAt(LocalDateTime.now());
                ticketStepRepository.save(targetStep);

                if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                    ticket.setStatus(TicketStatus.IN_PROGRESS);
                    ticket = ticketRepository.save(ticket);
                }
                notificationService.notifyStepStarted(targetStep, currentUser);

                String note = (request != null && request.getNote() != null) ? request.getNote() : "Bắt đầu thực hiện bước";
                TicketHistory history = ticketHistoryMapper.toTicketHistory(
                        ticket, currentUser, ActionType.WORK_STARTED, prevStatus, ticket.getStatus(), note
                );
                ticketHistoryRepository.save(history);

                return ticketMapper.toTicketResponse(ticket);
            } else if (targetStatus == TicketStatus.CANCELLED) {
                targetStep.setStatus(AssignmentStatus.CANCELLED);
                targetStep.setCompletedAt(LocalDateTime.now());
                targetStep.setUpdatedAt(LocalDateTime.now());
                ticketStepRepository.save(targetStep);

                String note = (request != null && request.getNote() != null) ? request.getNote() : "Hủy bước";
                TicketHistory history = ticketHistoryMapper.toTicketHistory(
                        ticket, currentUser, ActionType.CANCELLED, prevStatus, ticket.getStatus(), note
                );
                ticketHistoryRepository.save(history);

                return ticketMapper.toTicketResponse(ticket);
            }
        }

        // 2. Cập nhật trạng thái trực tiếp trên Ticket (requestedStepId == null)
        if (targetStatus == TicketStatus.COMPLETED || targetStatus == TicketStatus.RESOLVED) {
            // Không cho phép cập nhật trực tiếp Ticket sang COMPLETED / RESOLVED nếu tồn tại bất kỳ TicketStep nào chưa đạt AssignmentStatus.RESOLVED
            boolean allResolved = !steps.isEmpty() && steps.stream()
                    .filter(s -> !s.isDeleted())
                    .allMatch(s -> s.getStatus() == AssignmentStatus.RESOLVED || s.getStatus() == AssignmentStatus.COMPLETED);

            if (!allResolved) {
                boolean anyMissingEvidence = steps.stream()
                        .anyMatch(s -> s.getId() != null && !resolutionEvidenceRepository.existsByTicketStep_Id(s.getId()));
                if (anyMissingEvidence) {
                    throw new AppException(ErrorCode.EVIDENCE_REQUIRED);
                }
                throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
            }

            boolean hasAccess = admin || steps.stream().anyMatch(s -> isStepAssignedToUserOrDepartment(s, currentUser));
            if (!hasAccess) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }

            ticket.setStatus(targetStatus);
            ticket = ticketRepository.save(ticket);

            String note = (request != null && request.getNote() != null) ? request.getNote() : "Hoàn thành xử lý sự cố";
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, currentUser, ActionType.CLOSED, prevStatus, targetStatus, note
            );
            ticketHistoryRepository.save(history);
            sendTicketStatusNotification(ticket, targetStatus);

            return ticketMapper.toTicketResponse(ticket);
        }

        if (targetStatus == TicketStatus.IN_PROGRESS) {
            List<TicketStep> targetSteps = steps.stream()
                    .filter(s -> isActiveStatus(s.getStatus()) && (admin || isStepAssignedToUserOrDepartment(s, currentUser)))
                    .toList();
            if (!admin && targetSteps.isEmpty()) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            for (TicketStep step : targetSteps) {
                validateStepPrerequisites(step);
            }
            for (TicketStep step : targetSteps) {
                step.setStatus(AssignmentStatus.IN_PROGRESS);
                if (step.getStartedAt() == null) {
                    step.setStartedAt(LocalDateTime.now());
                }
                step.setUpdatedAt(LocalDateTime.now());
                ticketStepRepository.save(step);
                notificationService.notifyStepStarted(step, currentUser);
            }

            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket = ticketRepository.save(ticket);

            String note = (request != null && request.getNote() != null) ? request.getNote() : "Bắt đầu xử lý sự cố";
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, currentUser, ActionType.WORK_STARTED, prevStatus, TicketStatus.IN_PROGRESS, note
            );
            ticketHistoryRepository.save(history);

            return ticketMapper.toTicketResponse(ticket);
        }

        if (targetStatus == TicketStatus.CANCELLED || targetStatus == TicketStatus.REJECTED) {
            ticket.setStatus(targetStatus);
            ticket = ticketRepository.save(ticket);

            for (TicketStep s : steps) {
                if (isActiveStatus(s.getStatus())) {
                    s.setStatus(AssignmentStatus.CANCELLED);
                    s.setCompletedAt(LocalDateTime.now());
                    s.setUpdatedAt(LocalDateTime.now());
                    ticketStepRepository.save(s);
                }
            }

            String note = (request != null && request.getNote() != null) ? request.getNote() : "Hủy/từ chối sự cố";
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, currentUser, ActionType.CANCELLED, prevStatus, targetStatus, note
            );
            ticketHistoryRepository.save(history);
            sendTicketStatusNotification(ticket, targetStatus);

            return ticketMapper.toTicketResponse(ticket);
        }

        ticket.setStatus(targetStatus);
        ticket = ticketRepository.save(ticket);
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

            notificationService.createAndSendNotification(ticket, List.of(ticketUser), title, message);

            if (ticketUser.getEmail() != null) {
                emailService.sendTicketStatusEmail(ticketUser.getEmail(), title, message);
            }
        }
    }

    @Override
    @Transactional
    public List<ResolutionEvidenceResponse> submitEvidence(UUID stepId, List<MultipartFile> files, String resolutionNote) {
        User currentUser = getCurrentUser();

        TicketStep targetStep = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));
        Ticket ticket = targetStep.getTicket();

        if (targetStep.getAssignee() == null || !targetStep.getAssignee().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân công xử lý bước này.");
        }

        validateStepPrerequisites(targetStep);

        List<ImageUploadResponse> uploadResponses = ticketService.uploadImage(files, "cityfix/evidences");

        TicketStep finalStep = targetStep;
        List<ResolutionEvidence> evidences = uploadResponses.stream()
                .map(res -> evidenceMapper.toResolutionEvidence(finalStep, currentUser, res.getSecureUrl(), resolutionNote))
                .collect(Collectors.toList());

        evidences = resolutionEvidenceRepository.saveAll(evidences);

        TicketHistory history = ticketHistoryMapper.toTicketHistory(
                ticket, currentUser, ActionType.EVIDENCE_SUBMITTED, ticket.getStatus(), ticket.getStatus(), "Đã tải lên bằng chứng xử lý"
        );
        ticketHistoryRepository.save(history);

        return evidenceMapper.toResponseList(evidences);
    }
}
