package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.TicketHistoryRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.service.EmailService;
import com.cityfix.backend.service.TicketWorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketWorkflowEngineImpl implements TicketWorkflowEngine {

    private final TicketRepository ticketRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final com.cityfix.backend.service.NotificationService notificationService;

    @Override
    @Transactional
    public void handleStepCompletion(UUID stepId) {
        TicketStep completedStep = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        UUID ticketId = completedStep.getTicket().getTicketId();

        // Lock parent ticket with Pessimistic Write Lock to prevent concurrent workflow race conditions
        Ticket ticket = ticketRepository.findByIdWithPessimisticLock(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        completedStep.setTicket(ticket);
        LocalDateTime now = LocalDateTime.now();

        // Ensure completedStep is marked RESOLVED
        if (completedStep.getStatus() != AssignmentStatus.COMPLETED && completedStep.getStatus() != AssignmentStatus.RESOLVED) {
            completedStep.setStatus(AssignmentStatus.RESOLVED);
        }
        if (completedStep.getCompletedAt() == null) {
            completedStep.setCompletedAt(now);
        }
        completedStep.setUpdatedAt(now);
        ticketStepRepository.save(completedStep);

        // Fetch all steps for this ticket with dependencies eager-loaded
        List<TicketStep> steps = ticketStepRepository.findByTicket_TicketIdOrderByCreatedAtAscIdAsc(ticketId);
        if (steps == null || steps.isEmpty()) {
            steps = ticketStepRepository.findByTicket_TicketId(ticketId);
        }
        if (steps == null || steps.isEmpty()) {
            steps = ticketStepRepository.findByTicketTicketId(ticketId);
        }

        // 1. Kích hoạt song song các bước con phụ thuộc nếu TẤT CẢ các bước tiên quyết đã RESOLVED/COMPLETED
        List<TicketStep> activatedSteps = new ArrayList<>();
        for (TicketStep candidateStep : steps) {
            if (candidateStep.getId().equals(completedStep.getId())
                    || candidateStep.isDeleted()
                    || candidateStep.getStatus() == AssignmentStatus.COMPLETED
                    || candidateStep.getStatus() == AssignmentStatus.RESOLVED
                    || candidateStep.getStatus() == AssignmentStatus.CANCELLED
                    || candidateStep.getStatus() == AssignmentStatus.IN_PROGRESS) {
                continue;
            }

            boolean allPrerequisitesResolved = candidateStep.getDependsOn().stream()
                    .filter(dep -> !dep.isDeleted() && dep.getStatus() != AssignmentStatus.CANCELLED)
                    .allMatch(dep -> dep.getStatus() == AssignmentStatus.RESOLVED
                            || dep.getStatus() == AssignmentStatus.COMPLETED);

            if (allPrerequisitesResolved) {
                candidateStep.setStatus(AssignmentStatus.IN_PROGRESS);
                candidateStep.setStartedAt(now);
                if (candidateStep.getStep() != null && candidateStep.getStep().getDuration() != null && candidateStep.getStep().getDuration() > 0) {
                    candidateStep.setDeadline(now.plusHours(candidateStep.getStep().getDuration()));
                }
                candidateStep.setUpdatedAt(now);
                ticketStepRepository.save(candidateStep);
                activatedSteps.add(candidateStep);

                // Record history for each activated step
                User actor = completedStep.getAssignee() != null ? completedStep.getAssignee() : ticket.getUser();
                TicketHistory history = ticketHistoryMapper.toTicketHistory(
                        ticket, actor, ActionType.WORK_STARTED, TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS,
                        "Bước '" + (completedStep.getStep() != null ? completedStep.getStep().getStepName() : completedStep.getId())
                                + "' hoàn thành. Tự động kích hoạt bước '"
                                + (candidateStep.getStep() != null ? candidateStep.getStep().getStepName() : candidateStep.getId())
                                + "' sang Đang xử lý."
                );
                ticketHistoryRepository.save(history);

                notificationService.notifyStepStarted(candidateStep, candidateStep.getAssignee());
                log.info("Ticket {} - Step {} completed. Activated downstream step {}.", ticketId, completedStep.getId(), candidateStep.getId());
            }
        }

        // 2. Đánh giá trạng thái tổng thể của Ticket
        List<TicketStep> activeNonCancelledSteps = steps.stream()
                .filter(s -> !s.isDeleted() && s.getStatus() != AssignmentStatus.CANCELLED)
                .toList();

        boolean allStepsCompleted = !activeNonCancelledSteps.isEmpty() && activeNonCancelledSteps.stream()
                .allMatch(s -> s.getStatus() == AssignmentStatus.COMPLETED);

        boolean allStepsResolved = !activeNonCancelledSteps.isEmpty() && activeNonCancelledSteps.stream()
                .allMatch(s -> s.getStatus() == AssignmentStatus.RESOLVED || s.getStatus() == AssignmentStatus.COMPLETED);

        if (allStepsCompleted) {
            TicketStatus prevStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.COMPLETED);
            ticketRepository.save(ticket);

            User actor = completedStep.getAssignee() != null ? completedStep.getAssignee() : ticket.getUser();
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, actor, ActionType.CLOSED, prevStatus, TicketStatus.COMPLETED,
                    "Tất cả các bước xử lý của vé đã được quản lý xác nhận hoàn thành. Sự cố chuyển sang trạng thái Hoàn thành."
            );
            ticketHistoryRepository.save(history);

            notificationService.notifyStepCompleted(completedStep, completedStep.getAssignee(), true);
            log.info("Ticket {} - All steps COMPLETED. Ticket marked as COMPLETED.", ticketId);
        } else if (allStepsResolved) {
            TicketStatus prevStatus = ticket.getStatus();
            if (ticket.getStatus() != TicketStatus.RESOLVED) {
                ticket.setStatus(TicketStatus.RESOLVED);
                ticketRepository.save(ticket);

                User actor = completedStep.getAssignee() != null ? completedStep.getAssignee() : ticket.getUser();
                TicketHistory history = ticketHistoryMapper.toTicketHistory(
                        ticket, actor, ActionType.CLOSED, prevStatus, TicketStatus.RESOLVED,
                        "Tất cả các bước xử lý của vé đã hoàn tất. Sự cố chuyển sang trạng thái Đã giải quyết."
                );
                ticketHistoryRepository.save(history);
            }

            notificationService.notifyStepCompleted(completedStep, completedStep.getAssignee(), true);
            log.info("Ticket {} - All steps RESOLVED. Ticket marked as RESOLVED.", ticketId);
        } else {
            // Còn bước đang chờ hoặc đang làm dở -> duy trì trạng thái IN_PROGRESS
            if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                ticket.setStatus(TicketStatus.IN_PROGRESS);
            }

            // Cập nhật lại deadline tổng của Ticket từ max deadline của các bước hợp lệ
            LocalDateTime ticketDeadline = steps.stream()
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isDeleted())
                    .filter(s -> s.getStatus() != AssignmentStatus.CANCELLED)
                    .map(TicketStep::getDeadline)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            if (ticketDeadline != null) {
                ticket.setDeadline(ticketDeadline);
            }
            ticketRepository.save(ticket);

            notificationService.notifyStepCompleted(completedStep, completedStep.getAssignee(), false);
        }
    }
}
