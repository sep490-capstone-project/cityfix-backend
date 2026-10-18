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
    private NotificationService notificationService;

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
        verify(notificationService, times(1)).notifyStepCompleted(eq(mockStep1), any(), eq(false));
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

        workflowEngine.handleStepCompletion(mockStep2.getId());

        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        verify(ticketRepository, times(1)).save(mockTicket);
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
        verify(notificationService, times(1)).notifyStepCompleted(eq(mockStep2), any(), eq(true));
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

        workflowEngine.handleStepCompletion(mockStep1.getId());

        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        verify(ticketRepository, times(1)).save(mockTicket);
        verify(notificationService, times(1)).notifyStepCompleted(eq(mockStep1), any(), eq(true));
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

    @Test
    @DisplayName("step2CompletedWhileStep1InProgress_doesNotActivateStep3")
    void step2CompletedWhileStep1InProgress_doesNotActivateStep3() {
        mockStep1.setStatus(AssignmentStatus.IN_PROGRESS);
        mockStep2.setStatus(AssignmentStatus.COMPLETED);

        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .orderIndex(3)
                .status(AssignmentStatus.PENDING_PREVIOUS)
                .build();

        when(ticketStepRepository.findById(mockStep2.getId())).thenReturn(Optional.of(mockStep2));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));

        workflowEngine.handleStepCompletion(mockStep2.getId());

        // Step 3 MUST NOT be activated because Step 1 (orderIndex 1 < 3) is still IN_PROGRESS
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.PENDING_PREVIOUS);
        verify(ticketStepRepository, never()).save(mockStep3);
        assertThat(mockTicket.getStatus()).isNotEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    @DisplayName("step1CompletedAfterStep2_activatesStep3")
    void step1CompletedAfterStep2_activatesStep3() {
        // Step 2 is already completed earlier
        mockStep2.setStatus(AssignmentStatus.COMPLETED);

        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .orderIndex(3)
                .status(AssignmentStatus.PENDING_PREVIOUS)
                .build();

        // Now Step 1 completes
        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep1.getId());

        // Now both step 1 and step 2 are completed -> Step 3 must be activated
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        verify(ticketStepRepository, atLeastOnce()).save(mockStep3);
    }
}
