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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketWorkflowEngineImpl implements TicketWorkflowEngine {

    private final TicketRepository ticketRepository;
    private final TicketStepRepository ticketStepRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketHistoryMapper ticketHistoryMapper;
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    @Override
    @Transactional
    public void handleStepCompletion(UUID stepId) {
        TicketStep completedStep = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        UUID ticketId = completedStep.getTicket().getTicketId();

        // Lock parent ticket with Pessimistic Write Lock to prevent concurrent workflow race conditions
        Ticket ticket = ticketRepository.findByIdWithPessimisticLock(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();

        // Ensure completedStep is marked COMPLETED
        if (completedStep.getStatus() != AssignmentStatus.COMPLETED && completedStep.getStatus() != AssignmentStatus.RESOLVED) {
            completedStep.setStatus(AssignmentStatus.COMPLETED);
        }
        if (completedStep.getCompletedAt() == null) {
            completedStep.setCompletedAt(now);
        }
        completedStep.setUpdatedAt(now);
        ticketStepRepository.save(completedStep);

        // Fetch all steps for this ticket ordered by orderIndex
        List<TicketStep> steps = ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(ticketId);

        int currentOrder = completedStep.getOrderIndex() != null ? completedStep.getOrderIndex() : 0;

        // Find next step in sequence that is not COMPLETED/RESOLVED/CANCELLED
        TicketStep nextStep = steps.stream()
                .filter(s -> !s.getId().equals(completedStep.getId()))
                .filter(s -> s.getStatus() != AssignmentStatus.COMPLETED
                        && s.getStatus() != AssignmentStatus.RESOLVED
                        && s.getStatus() != AssignmentStatus.CANCELLED)
                .filter(s -> s.getOrderIndex() == null || s.getOrderIndex() > currentOrder)
                .min(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        // If no step with orderIndex > currentOrder found, check any remaining uncompleted step
        if (nextStep == null) {
            nextStep = steps.stream()
                    .filter(s -> !s.getId().equals(completedStep.getId()))
                    .filter(s -> s.getStatus() != AssignmentStatus.COMPLETED
                            && s.getStatus() != AssignmentStatus.RESOLVED
                            && s.getStatus() != AssignmentStatus.CANCELLED)
                    .min(Comparator.comparing(TicketStep::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        }

        if (nextStep != null) {
            // Activate next step
            nextStep.setStatus(AssignmentStatus.IN_PROGRESS);
            if (nextStep.getStartedAt() == null) {
                nextStep.setStartedAt(now);
            }
            nextStep.setUpdatedAt(now);
            ticketStepRepository.save(nextStep);

            // Ensure ticket is IN_PROGRESS
            TicketStatus prevStatus = ticket.getStatus();
            if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                ticket.setStatus(TicketStatus.IN_PROGRESS);
                ticketRepository.save(ticket);
            }

            // Record history
            User actor = completedStep.getAssignee() != null ? completedStep.getAssignee() : ticket.getUser();
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, actor, ActionType.WORK_STARTED, prevStatus, TicketStatus.IN_PROGRESS,
                    "Bước '" + (completedStep.getStep() != null ? completedStep.getStep().getStepName() : completedStep.getId())
                            + "' hoàn thành. Tự động chuyển bước '"
                            + (nextStep.getStep() != null ? nextStep.getStep().getStepName() : nextStep.getId())
                            + "' sang Đang xử lý."
            );
            ticketHistoryRepository.save(history);

            log.info("Ticket {} - Step {} completed. Activated next step {}.", ticketId, completedStep.getId(), nextStep.getId());
        } else {
            // All steps completed -> resolve ticket
            TicketStatus prevStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.RESOLVED);
            ticketRepository.save(ticket);

            User actor = completedStep.getAssignee() != null ? completedStep.getAssignee() : ticket.getUser();
            TicketHistory history = ticketHistoryMapper.toTicketHistory(
                    ticket, actor, ActionType.CLOSED, prevStatus, TicketStatus.RESOLVED,
                    "Tất cả các bước xử lý của vé đã hoàn tất. Sự cố chuyển sang trạng thái Đã giải quyết."
            );
            ticketHistoryRepository.save(history);

            sendResolvedNotification(ticket);

            log.info("Ticket {} - All steps completed. Ticket marked as RESOLVED.", ticketId);
        }
    }

    private void sendResolvedNotification(Ticket ticket) {
        User ticketUser = ticket.getUser();
        if (ticketUser != null) {
            String title = "Sự cố đã được giải quyết";
            String message = "Toàn bộ các bước xử lý của sự cố đã được đơn vị hoàn tất. Vui lòng kiểm tra và đánh giá kết quả.";

            Notification notification = Notification.builder()
                    .user(ticketUser)
                    .title(title)
                    .content(message)
                    .isRead(false)
                    .build();
            notification = notificationRepository.save(notification);

            try {
                messagingTemplate.convertAndSend("/topic/notifications/" + ticketUser.getUserId(), notificationMapper.toResponse(notification));
            } catch (Exception e) {
                log.error("Lỗi khi gửi websocket notification: {}", e.getMessage());
            }

            if (ticketUser.getEmail() != null) {
                try {
                    emailService.sendTicketStatusEmail(ticketUser.getEmail(), title, message);
                } catch (Exception e) {
                    log.error("Lỗi khi gửi email notification: {}", e.getMessage());
                }
            }
        }
    }
}
