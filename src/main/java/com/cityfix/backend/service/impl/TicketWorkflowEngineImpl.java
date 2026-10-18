package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.NotificationRepository;
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

        // Fetch all steps for this ticket ordered by orderIndex
        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(ticketId);

        // Lọc tất cả các bước chưa kết thúc (không phải COMPLETED, RESOLVED, CANCELLED)
        List<TicketStep> remainingSteps = steps.stream()
                .filter(s -> !s.getId().equals(completedStep.getId()))
                .filter(s -> s.getStatus() != AssignmentStatus.COMPLETED
                        && s.getStatus() != AssignmentStatus.RESOLVED
                        && s.getStatus() != AssignmentStatus.CANCELLED)
                .sorted(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<TicketStep> activeNonCancelledSteps = steps.stream()
                .filter(s -> !s.isDeleted() && s.getStatus() != AssignmentStatus.CANCELLED)
                .toList();

        boolean allStepsCompleted = !activeNonCancelledSteps.isEmpty() && activeNonCancelledSteps.stream()
                .allMatch(s -> s.getStatus() == AssignmentStatus.COMPLETED);

        boolean allStepsResolved = !activeNonCancelledSteps.isEmpty() && activeNonCancelledSteps.stream()
                .allMatch(s -> s.getStatus() == AssignmentStatus.RESOLVED || s.getStatus() == AssignmentStatus.COMPLETED);

        if (remainingSteps.isEmpty() && allStepsCompleted) {
            // Tất cả các bước đã được Manager xác nhận COMPLETED -> chuyển trạng thái vé sang COMPLETED
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
        } else if (remainingSteps.isEmpty() && allStepsResolved) {
            // Tất cả các bước đã hoàn tất (RESOLVED) -> chuyển trạng thái vé sang RESOLVED
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
        } else if (!remainingSteps.isEmpty()) {
            // Có ít nhất một bước chưa hoàn tất, chọn bước kế tiếp có orderIndex nhỏ nhất
            TicketStep candidateNextStep = remainingSteps.get(0);
            int candidateOrder = candidateNextStep.getOrderIndex() != null ? candidateNextStep.getOrderIndex() : 0;

            // Tiền điều kiện nghiêm ngặt: Kiểm tra xem TẤT CẢ các bước có orderIndex < candidateOrder đều đã kết thúc
            boolean allPriorStepsFinished = steps.stream()
                    .filter(s -> !s.getId().equals(candidateNextStep.getId()))
                    .filter(s -> s.getOrderIndex() != null && s.getOrderIndex() < candidateOrder)
                    .allMatch(s -> s.getStatus() == AssignmentStatus.COMPLETED
                            || s.getStatus() == AssignmentStatus.RESOLVED
                            || s.getStatus() == AssignmentStatus.CANCELLED);

            if (allPriorStepsFinished) {
                // Đủ điều kiện kích hoạt: chỉ chuyển sang IN_PROGRESS nếu chưa ở trạng thái này
                if (candidateNextStep.getStatus() != AssignmentStatus.IN_PROGRESS) {
                    candidateNextStep.setStatus(AssignmentStatus.IN_PROGRESS);
                    candidateNextStep.setStartedAt(now);
                    if (candidateNextStep.getStep() != null && candidateNextStep.getStep().getDuration() != null && candidateNextStep.getStep().getDuration() > 0) {
                        candidateNextStep.setDeadline(now.plusHours(candidateNextStep.getStep().getDuration()));
                    }
                    candidateNextStep.setUpdatedAt(now);
                    ticketStepRepository.save(candidateNextStep);

                    // Cascade deadlines cho các bước phía sau (orderIndex > candidateOrder)
                    LocalDateTime prevDeadline = candidateNextStep.getDeadline();
                    if (prevDeadline != null) {
                        List<TicketStep> subsequentSteps = steps.stream()
                                .filter(s -> !s.getId().equals(candidateNextStep.getId()))
                                .filter(s -> !s.isDeleted())
                                .filter(s -> s.getOrderIndex() != null && s.getOrderIndex() > candidateOrder)
                                .filter(s -> s.getStatus() != AssignmentStatus.COMPLETED
                                        && s.getStatus() != AssignmentStatus.RESOLVED
                                        && s.getStatus() != AssignmentStatus.CANCELLED)
                                .sorted(Comparator.comparing(TicketStep::getOrderIndex))
                                .toList();

                        for (TicketStep s : subsequentSteps) {
                            int duration = (s.getStep() != null && s.getStep().getDuration() != null && s.getStep().getDuration() > 0)
                                    ? s.getStep().getDuration()
                                    : 0;
                            LocalDateTime nextStart = prevDeadline;
                            LocalDateTime nextDeadline = nextStart.plusHours(duration);
                            s.setStartedAt(nextStart);
                            s.setDeadline(nextDeadline);
                            s.setUpdatedAt(now);
                            ticketStepRepository.save(s);
                            prevDeadline = nextDeadline;
                        }
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

                    // Ensure ticket is IN_PROGRESS
                    TicketStatus prevStatus = ticket.getStatus();
                    if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                        ticket.setStatus(TicketStatus.IN_PROGRESS);
                    }
                    ticketRepository.save(ticket);

                    // Record history
                    User actor = completedStep.getAssignee() != null ? completedStep.getAssignee() : ticket.getUser();
                    TicketHistory history = ticketHistoryMapper.toTicketHistory(
                            ticket, actor, ActionType.WORK_STARTED, prevStatus, TicketStatus.IN_PROGRESS,
                            "Bước '" + (completedStep.getStep() != null ? completedStep.getStep().getStepName() : completedStep.getId())
                                    + "' hoàn thành. Tự động chuyển bước '"
                                    + (candidateNextStep.getStep() != null ? candidateNextStep.getStep().getStepName() : candidateNextStep.getId())
                                    + "' sang Đang xử lý."
                    );
                    ticketHistoryRepository.save(history);

                    log.info("Ticket {} - Step {} completed. Activated next step {}.", ticketId, completedStep.getId(), candidateNextStep.getId());
                }
            } else {
                log.warn("Ticket {} - Step {} completed, but cannot activate next step {} because prior steps are not completed yet.",
                        ticketId, completedStep.getId(), candidateNextStep.getId());
            }

            notificationService.notifyStepCompleted(completedStep, completedStep.getAssignee(), false);
        }
    }
}
