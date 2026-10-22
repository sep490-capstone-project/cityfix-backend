package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketHistory;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.ResolutionEvidenceRepository;
import com.cityfix.backend.repository.TicketHistoryRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.TicketService;
import com.cityfix.backend.service.TicketStepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cityfix.backend.util.SecurityUtils;

import com.cityfix.backend.mapper.EvidenceMapper;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse;
import com.cityfix.backend.model.entity.ResolutionEvidence;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketStepServiceImpl implements TicketStepService {

    private final TicketStepRepository ticketStepRepository;
    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final UserRepository userRepository;
    private final ResolutionEvidenceRepository resolutionEvidenceRepository;
    private final TicketStepMapper ticketStepMapper;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final EvidenceMapper evidenceMapper;
    private final TicketService ticketService;
    private final com.cityfix.backend.service.TicketWorkflowEngine ticketWorkflowEngine;
    private final com.cityfix.backend.service.NotificationService notificationService;

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .or(SecurityUtils::getCurrentUserPrincipal)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void validateStepPermission(TicketStep step, User currentUser) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        String roleName = currentUser.getRole().getRoleName();
        if (!"STAFF".equalsIgnoreCase(roleName)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (step.getAssignee() == null
                || currentUser.getUserId() == null
                || !currentUser.getUserId().equals(step.getAssignee().getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân công xử lý bước này.");
        }
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

    @Override
    @Transactional
    public TicketStepResponse updateTicketStepStatus(UUID stepId, AssignmentStatus status) {
        TicketStep step = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        User currentUser = getCurrentUser();
        validateStepPermission(step, currentUser);

        if (status == AssignmentStatus.IN_PROGRESS || status == AssignmentStatus.RESOLVED || status == AssignmentStatus.COMPLETED) {
            validateStepPrerequisites(step);
        }

        AssignmentStatus prevStatus = step.getStatus();

        if (status == AssignmentStatus.RESOLVED) {
            if (!resolutionEvidenceRepository.existsByTicketStep_Id(stepId)) {
                throw new AppException(ErrorCode.EVIDENCE_REQUIRED);
            }
        }

        step.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        step.setUpdatedAt(now);

        if (status == AssignmentStatus.IN_PROGRESS) {
            if (step.getStartedAt() == null) {
                step.setStartedAt(now);
            }
        } else if (status == AssignmentStatus.COMPLETED
                || status == AssignmentStatus.RESOLVED
                || status == AssignmentStatus.CANCELLED) {
            if (step.getCompletedAt() == null) {
                step.setCompletedAt(now);
            }
        }

        step = ticketStepRepository.save(step);

        Ticket ticket = step.getTicket();
        if (ticket != null) {
            TicketStatus prevTicketStatus = ticket.getStatus();
            if (status == AssignmentStatus.IN_PROGRESS && prevTicketStatus == TicketStatus.ASSIGNED) {
                ticket.setStatus(TicketStatus.IN_PROGRESS);
                if (ticketHistoryMapper != null && ticketHistoryRepository != null) {
                    TicketHistory history = ticketHistoryMapper.toTicketHistory(
                            ticket, currentUser, ActionType.WORK_STARTED, prevTicketStatus, TicketStatus.IN_PROGRESS,
                            "Bước xử lý bắt đầu thực hiện, chuyển trạng thái sự cố sang Đang xử lý."
                    );
                    if (history != null) {
                        ticketHistoryRepository.save(history);
                    }
                }
            }
            ticket.setDeadline(ticketService.calculateTicketDeadline(ticket));
            ticketRepository.save(ticket);
        }

        if (status == AssignmentStatus.IN_PROGRESS && prevStatus != AssignmentStatus.IN_PROGRESS) {
            TicketStep stepWithDetails = ticketStepRepository.findByIdWithAssigneeAndDepartment(stepId)
                    .orElse(step);
            User staffToNotify = (stepWithDetails.getAssignee() != null)
                    ? stepWithDetails.getAssignee()
                    : currentUser;
            notificationService.notifyStepStarted(stepWithDetails, staffToNotify);
        } else if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.RESOLVED) {
            ticketWorkflowEngine.handleStepCompletion(step.getId());
            step = ticketStepRepository.findById(stepId).orElse(step);
        }

        return ticketStepMapper.toTicketStepResponse(step);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketStepResponse> getTicketStepsByTicketId(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new AppException(ErrorCode.TICKET_NOT_FOUND);
        }
        List<TicketStep> steps = ticketStepRepository.findByTicketTicketId(ticketId);
        return ticketStepMapper.toTicketStepResponseList(steps);
    }

    @Override
    @Transactional
    public void deleteTicketStep(UUID stepId) {
        User currentUser = getCurrentUser();
        if (currentUser.getRole() == null || !"MANAGER".equalsIgnoreCase(currentUser.getRole().getRoleName())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        TicketStep step = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        ticketStepRepository.delete(step);

        Ticket ticket = step.getTicket();
        if (ticket != null) {
            ticket.setDeadline(ticketService.calculateTicketDeadline(ticket));
            ticketRepository.save(ticket);
        }
    }

    @Override
    @Transactional
    public void cascadeCancelTicketSteps(UUID ticketId) {
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
    @Transactional
    public List<ResolutionEvidenceResponse> uploadEvidences(UUID stepId, List<MultipartFile> files, String resolutionNote) {
        TicketStep step = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        User currentUser = getCurrentUser();
        if (step.getAssignee() == null || !step.getAssignee().getUserId().equals(currentUser.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không được phân công xử lý bước này.");
        }

        validateStepPrerequisites(step);

        List<ImageUploadResponse> uploadResponses = ticketService.uploadImage(files, "cityfix/evidences");

        List<ResolutionEvidence> evidences = uploadResponses.stream()
                .map(res -> evidenceMapper.toResolutionEvidence(step, currentUser, res.getSecureUrl(), resolutionNote))
                .toList();

        evidences = resolutionEvidenceRepository.saveAll(evidences);

        Ticket ticket = step.getTicket();
        if (ticket != null && ticketHistoryMapper != null && ticketHistoryRepository != null) {
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, currentUser, ActionType.EVIDENCE_SUBMITTED, ticket.getStatus(), ticket.getStatus(), "Đã tải lên bằng chứng xử lý"
            );
            if (history != null) {
                ticketHistoryRepository.save(history);
            }
        }

        return evidenceMapper.toResponseList(evidences);
    }

    @Override
    @Transactional
    public List<ResolutionEvidenceResponse> submitEvidence(UUID stepId, List<MultipartFile> files, String resolutionNote) {
        return uploadEvidences(stepId, files, resolutionNote);
    }
}
