package com.cityfix.backend.service;

import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.NotificationRepository;
import com.cityfix.backend.repository.TicketHistoryRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.service.impl.TicketWorkflowEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketWorkflowEngineTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private TicketHistoryMapper ticketHistoryMapper;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private TicketWorkflowEngineImpl workflowEngine;

    private Ticket mockTicket;
    private User mockCitizen;
    private Step mockStep1Def;
    private Step mockStep2Def;
    private TicketStep mockStep1;
    private TicketStep mockStep2;

    @BeforeEach
    void setUp() {
        mockCitizen = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Nguyễn Văn A")
                .email("citizen@example.com")
                .build();

        mockTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .user(mockCitizen)
                .status(TicketStatus.IN_PROGRESS)
                .build();

        mockStep1Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 1: Khảo sát").build();
        mockStep2Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 2: Sửa chữa").build();

        mockStep1 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep1Def)
                .orderIndex(1)
                .status(AssignmentStatus.COMPLETED)
                .build();

        mockStep2 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep2Def)
                .orderIndex(2)
                .status(AssignmentStatus.PENDING_PREVIOUS)
                .build();
    }

    @Test
    @DisplayName("stepCompleted_transitionsNextStepToInProgress")
    void stepCompleted_transitionsNextStepToInProgress() {
        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep1.getId());

        assertThat(mockStep2.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(mockStep2.getStartedAt()).isNotNull();
        verify(ticketStepRepository, atLeastOnce()).save(mockStep2);
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("lastStepCompleted_resolvesOverallTicket")
    void lastStepCompleted_resolvesOverallTicket() {
        mockStep2.setStatus(AssignmentStatus.COMPLETED);

        when(ticketStepRepository.findById(mockStep2.getId())).thenReturn(Optional.of(mockStep2));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        workflowEngine.handleStepCompletion(mockStep2.getId());

        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        verify(ticketRepository, times(1)).save(mockTicket);
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(emailService, times(1)).sendTicketStatusEmail(eq("citizen@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("singleStepTicket_resolvesImmediately")
    void singleStepTicket_resolvesImmediately() {
        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        workflowEngine.handleStepCompletion(mockStep1.getId());

        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        verify(ticketRepository, times(1)).save(mockTicket);
    }

    @Test
    @DisplayName("stepCompleted_skipsCancelledStepAndActivatesNextActiveStep")
    void stepCompleted_skipsCancelledStepAndActivatesNextActiveStep() {
        mockStep2.setStatus(AssignmentStatus.CANCELLED);

        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .orderIndex(3)
                .status(AssignmentStatus.PENDING_PREVIOUS)
                .build();

        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep1.getId());

        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        verify(ticketStepRepository, atLeastOnce()).save(mockStep3);
    }
}
