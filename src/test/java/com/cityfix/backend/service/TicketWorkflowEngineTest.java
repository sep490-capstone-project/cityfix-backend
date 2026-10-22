package com.cityfix.backend.service;

import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        mockStep1Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 1: Khảo sát").duration(4).build();
        mockStep2Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 2: Sửa chữa").duration(6).build();

        mockStep1 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep1Def)
                .status(AssignmentStatus.COMPLETED)
                .dependsOn(new HashSet<>())
                .build();

        mockStep2 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep2Def)
                .status(AssignmentStatus.ASSIGNED)
                .dependsOn(new HashSet<>(List.of(mockStep1)))
                .build();
    }

    @Test
    @DisplayName("stepCompleted_transitionsDependentStepToInProgress")
    void stepCompleted_transitionsDependentStepToInProgress() {
        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep1.getId());

        assertThat(mockStep2.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(mockStep2.getStartedAt()).isNotNull();
        assertThat(mockStep2.getDeadline()).isEqualTo(mockStep2.getStartedAt().plusHours(6));
        verify(ticketStepRepository, atLeastOnce()).save(mockStep2);
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
        verify(notificationService, times(1)).notifyStepStarted(eq(mockStep2), any());
        verify(notificationService, times(1)).notifyStepCompleted(eq(mockStep1), any(), eq(false));
    }

    @Test
    @DisplayName("stepCompleted_parallelFanOut_activatesMultipleDownstreamStepsConcurrently")
    void stepCompleted_parallelFanOut_activatesMultipleDownstreamStepsConcurrently() {
        // Step 3 also depends on Step 1 (Fan-out)
        Step mockStep3Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 3: Lắp đặt biển báo").duration(2).build();
        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep3Def)
                .status(AssignmentStatus.ASSIGNED)
                .dependsOn(new HashSet<>(List.of(mockStep1)))
                .build();

        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep1.getId());

        // Both Step 2 and Step 3 must be activated to IN_PROGRESS simultaneously
        assertThat(mockStep2.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        verify(ticketStepRepository).save(mockStep2);
        verify(ticketStepRepository).save(mockStep3);
        verify(notificationService).notifyStepStarted(eq(mockStep2), any());
        verify(notificationService).notifyStepStarted(eq(mockStep3), any());
    }

    @Test
    @DisplayName("stepCompleted_fanIn_doesNotActivateUntilAllPrerequisitesResolved")
    void stepCompleted_fanIn_doesNotActivateUntilAllPrerequisitesResolved() {
        // Step 3 depends on both Step 1 and Step 2
        Step mockStep3Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 3: Nghiệm thu").duration(2).build();
        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep3Def)
                .status(AssignmentStatus.ASSIGNED)
                .dependsOn(new HashSet<>(List.of(mockStep1, mockStep2)))
                .build();

        // Step 2 is still IN_PROGRESS (not resolved)
        mockStep2.setStatus(AssignmentStatus.IN_PROGRESS);

        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));

        workflowEngine.handleStepCompletion(mockStep1.getId());

        // Step 3 MUST remain ASSIGNED because Step 2 is not RESOLVED/COMPLETED
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        verify(ticketStepRepository, never()).save(mockStep3);
    }

    @Test
    @DisplayName("stepCompleted_fanIn_activatesWhenLastPrerequisiteCompletes")
    void stepCompleted_fanIn_activatesWhenLastPrerequisiteCompletes() {
        // Step 3 depends on both Step 1 and Step 2
        Step mockStep3Def = Step.builder().stepId(UUID.randomUUID()).stepName("Bước 3: Nghiệm thu").duration(2).build();
        TicketStep mockStep3 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .step(mockStep3Def)
                .status(AssignmentStatus.ASSIGNED)
                .dependsOn(new HashSet<>(List.of(mockStep1, mockStep2)))
                .build();

        // Step 1 was already COMPLETED earlier
        mockStep1.setStatus(AssignmentStatus.COMPLETED);
        // Now Step 2 completes
        mockStep2.setStatus(AssignmentStatus.RESOLVED);

        when(ticketStepRepository.findById(mockStep2.getId())).thenReturn(Optional.of(mockStep2));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
                .thenReturn(List.of(mockStep1, mockStep2, mockStep3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());

        workflowEngine.handleStepCompletion(mockStep2.getId());

        // Now both Step 1 and Step 2 are satisfied -> Step 3 must be activated
        assertThat(mockStep3.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        verify(ticketStepRepository, atLeastOnce()).save(mockStep3);
    }

    @Test
    @DisplayName("lastStepCompleted_resolvesOverallTicket")
    void lastStepCompleted_resolvesOverallTicket() {
        mockStep1.setStatus(AssignmentStatus.COMPLETED);
        mockStep2.setStatus(AssignmentStatus.COMPLETED);

        when(ticketStepRepository.findById(mockStep2.getId())).thenReturn(Optional.of(mockStep2));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
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
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
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
        mockStep1.setStatus(AssignmentStatus.COMPLETED);

        when(ticketStepRepository.findById(mockStep1.getId())).thenReturn(Optional.of(mockStep1));
        when(ticketRepository.findByIdWithPessimisticLock(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicket_TicketId(mockTicket.getTicketId()))
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
}
