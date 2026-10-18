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

import java.time.LocalDateTime;
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
                .status(AssignmentStatus.ASSIGNED)
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
    @DisplayName("allStepsResolved_transitionsTicketToResolved")
    void allStepsResolved_transitionsTicketToResolved() {
        mockStep1.setStatus(AssignmentStatus.RESOLVED);
        mockStep2.setStatus(AssignmentStatus.RESOLVED);

        when(ticketStepRepository.findById(mockStep2.getId())).thenReturn(Optional.of(mockStep2));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep2.getId());

        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
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
                .status(AssignmentStatus.ASSIGNED)
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
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(ticketStepRepository.findById(mockStep2.getId())).thenReturn(Optional.of(mockStep2));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));

        workflowEngine.handleStepCompletion(mockStep2.getId());

        // Step 3 MUST NOT be activated because Step 1 (orderIndex 1 < 3) is still IN_PROGRESS
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
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
                .status(AssignmentStatus.ASSIGNED)
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

    @Test
    @DisplayName("earlyStepCompletion_recalculatesNextStepTimelineAndCascadesSubsequentSteps")
    void earlyStepCompletion_recalculatesNextStepTimelineAndCascadesSubsequentSteps() {
        LocalDateTime initialCreatedAt = LocalDateTime.now().minusHours(2);
        mockTicket.setCreatedAt(initialCreatedAt);

        // Step 1: duration 4h, started 2h ago, deadline initialCreatedAt + 4h (still 2h in the future)
        mockStep1Def.setDuration(4);
        mockStep1.setStartedAt(initialCreatedAt);
        mockStep1.setDeadline(initialCreatedAt.plusHours(4));
        mockStep1.setStatus(AssignmentStatus.COMPLETED);

        // Step 2: duration 6h, planned startedAt was initialCreatedAt + 4h, planned deadline was initialCreatedAt + 10h
        mockStep2Def.setDuration(6);
        mockStep2.setStartedAt(initialCreatedAt.plusHours(4));
        mockStep2.setDeadline(initialCreatedAt.plusHours(10));
        mockStep2.setStatus(AssignmentStatus.ASSIGNED);

        // Step 3: duration 3h, planned startedAt initialCreatedAt + 10h, planned deadline initialCreatedAt + 13h
        Step mockStep3Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 3: Nghiệm thu").duration(3).build();
        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep3Def)
                .orderIndex(3)
                .startedAt(initialCreatedAt.plusHours(10))
                .deadline(initialCreatedAt.plusHours(13))
                .status(AssignmentStatus.ASSIGNED)
                .build();

        mockTicket.setDeadline(initialCreatedAt.plusHours(13));

        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByOrderIndexAsc(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        LocalDateTime beforeExecution = LocalDateTime.now().minusSeconds(1);
        workflowEngine.handleStepCompletion(mockStep1.getId());
        LocalDateTime afterExecution = LocalDateTime.now().plusSeconds(1);

        // Step 2 should be IN_PROGRESS, startedAt should be NOW (between beforeExecution and afterExecution)
        assertThat(mockStep2.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(mockStep2.getStartedAt()).isAfterOrEqualTo(beforeExecution);
        assertThat(mockStep2.getStartedAt()).isBeforeOrEqualTo(afterExecution);
        // Step 2 deadline should be startedAt + 6 hours
        assertThat(mockStep2.getDeadline()).isEqualTo(mockStep2.getStartedAt().plusHours(6));

        // Step 3 should be ASSIGNED, startedAt should be Step 2's new deadline, deadline should be Step 3 startedAt + 3h
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(mockStep3.getStartedAt()).isEqualTo(mockStep2.getDeadline());
        assertThat(mockStep3.getDeadline()).isEqualTo(mockStep3.getStartedAt().plusHours(3));

        // Ticket deadline should be equal to the last step's deadline
        assertThat(mockTicket.getDeadline()).isEqualTo(mockStep3.getDeadline());
        verify(ticketRepository).save(mockTicket);
    }
}
