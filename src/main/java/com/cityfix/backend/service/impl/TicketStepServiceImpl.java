package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.AssignmentStatus;
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
    private final UserRepository userRepository;
    private final TicketStepMapper ticketStepMapper;
    private final TicketService ticketService;
    private final com.cityfix.backend.service.TicketWorkflowEngine ticketWorkflowEngine;
    private final com.cityfix.backend.service.NotificationService notificationService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
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
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    @Override
    @Transactional
    public TicketStepResponse updateTicketStepStatus(UUID stepId, AssignmentStatus status) {
        TicketStep step = ticketStepRepository.findById(stepId)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        User currentUser = getCurrentUser();
        validateStepPermission(step, currentUser);

        AssignmentStatus prevStatus = step.getStatus();
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
            ticket.setDeadline(ticketService.calculateTicketDeadline(ticket));
            ticketRepository.save(ticket);
        }

        if (status == AssignmentStatus.IN_PROGRESS && prevStatus != AssignmentStatus.IN_PROGRESS) {
            notificationService.notifyStepStarted(step, currentUser);
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
}
