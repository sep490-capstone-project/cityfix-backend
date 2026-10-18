package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.EvidenceMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.model.dto.request.UpdateStaffTicketStatusRequest;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.ActionType;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.impl.StaffTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StaffTicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private ResolutionEvidenceRepository resolutionEvidenceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private TicketHistoryMapper ticketHistoryMapper;
    @Mock
    private EvidenceMapper evidenceMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private TicketService ticketService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.cityfix.backend.service.TicketWorkflowEngine ticketWorkflowEngine;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private StaffTicketServiceImpl staffTicketService;

    private User staffUser;
    private Department department;
    private Ticket ticket;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setDepartmentId(UUID.randomUUID());

        Role staffRole = new Role(UUID.randomUUID(), "STAFF");

        staffUser = new User();
        staffUser.setUserId(UUID.randomUUID());
        staffUser.setEmail("staff@cityfix.com");
        staffUser.setRole(staffRole);
        staffUser.setDepartment(department);

        ticketId = UUID.randomUUID();
        ticket = new Ticket();
        ticket.setTicketId(ticketId);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setSteps(new ArrayList<>());
        
        User reporter = new User();
        reporter.setUserId(UUID.randomUUID());
        reporter.setEmail("reporter@cityfix.com");
        ticket.setUser(reporter);
    }

    private void setupSecurityContext() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(staffUser.getEmail(), "password", Collections.emptyList())
        );
        when(userRepository.findByEmail(staffUser.getEmail())).thenReturn(Optional.of(staffUser));
    }

    private void setupActiveAssignment() {
        TicketStep step = new TicketStep();
        step.setId(UUID.randomUUID());
        step.setStatus(AssignmentStatus.ASSIGNED);
        step.setAssignee(staffUser);
        step.setTicket(ticket);
        ticket.getSteps().add(step);
        lenient().when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByOrderIndexAsc(any(), any()))
                .thenReturn(List.of(step));
        lenient().when(ticketStepRepository.findByTicketTicketIdAndStatusIn(any(), any()))
                .thenReturn(List.of(step));
    }

    @Test
    @DisplayName("updateTicketStatus_in_progress_success")
    void updateTicketStatus_in_progress_success() {
        setupSecurityContext();
        setupActiveAssignment();

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest("bắt đầu");

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        TicketResponse mockResponse = TicketResponse.builder().build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = staffTicketService.updateTicketStatus(ticketId, TicketStatus.IN_PROGRESS, request);

        assertThat(response).isNotNull();
        verify(ticketHistoryRepository).save(any());
        verify(ticketRepository).save(any());
        verify(notificationService).notifyStepStarted(any(TicketStep.class), eq(staffUser));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("updateTicketStatus_forbidden")
    void updateTicketStatus_forbidden() {
        setupSecurityContext();

        // No active assignment matching the staff's department
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();

        assertThatThrownBy(() -> staffTicketService.updateTicketStatus(ticketId, TicketStatus.IN_PROGRESS, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStatus_completed_without_evidence_throws_exception")
    void updateTicketStatus_completed_missing_evidence() {
        setupSecurityContext();
        setupActiveAssignment();

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        UUID stepId = ticket.getSteps().get(0).getId();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(resolutionEvidenceRepository.existsByTicketStep_Id(stepId)).thenReturn(false);

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();

        assertThatThrownBy(() -> staffTicketService.updateTicketStatus(ticketId, TicketStatus.COMPLETED, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVIDENCE_REQUIRED);
    }

    @Test
    @DisplayName("updateTicketStatus_completed_with_evidence_success")
    void updateTicketStatus_completed_with_evidence_success() {
        setupSecurityContext();
        setupActiveAssignment();

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.getSteps().get(0).setStatus(AssignmentStatus.RESOLVED);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        
        TicketResponse mockResponse = TicketResponse.builder().status(TicketStatus.COMPLETED).build();
        when(ticketMapper.toTicketResponse(any())).thenReturn(mockResponse);

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();
        staffTicketService.updateTicketStatus(ticketId, TicketStatus.COMPLETED, request);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        verify(ticketHistoryRepository).save(any());
    }

    @Test
    @DisplayName("updateTicketStatus_invalid_transition_throws_exception")
    void updateTicketStatus_invalid_transition() {
        setupSecurityContext();
        setupActiveAssignment();

        // Current status is ASSIGNED, trying to jump to COMPLETED
        ticket.setStatus(TicketStatus.ASSIGNED);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();

        assertThatThrownBy(() -> staffTicketService.updateTicketStatus(ticketId, TicketStatus.COMPLETED, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("submitEvidence_multipart_success")
    void submitEvidence_multipart_success() {
        setupSecurityContext();
        setupActiveAssignment();

        MultipartFile mockFile = mock(MultipartFile.class);
        List<MultipartFile> files = List.of(mockFile);
        
        ImageUploadResponse uploadRes = ImageUploadResponse.builder().secureUrl("http://image.url").build();
        ResolutionEvidence mockEvidence = new ResolutionEvidence();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketService.uploadImage(any(), eq("cityfix/evidences"))).thenReturn(List.of(uploadRes));
        when(evidenceMapper.toResolutionEvidence(any(), any(), any(), any())).thenReturn(mockEvidence);
        when(resolutionEvidenceRepository.saveAll(any())).thenReturn(new ArrayList<>());
        
        staffTicketService.submitEvidence(ticketId, files, "Note");

        verify(ticketService).uploadImage(any(), eq("cityfix/evidences"));
        verify(evidenceMapper).toResolutionEvidence(any(), any(), eq("http://image.url"), eq("Note"));
        verify(resolutionEvidenceRepository).saveAll(anyList());
        verify(ticketHistoryRepository).save(any());
        verify(ticketWorkflowEngine, never()).handleStepCompletion(any());
        assertThat(ticket.getSteps().get(0).getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    @DisplayName("updateTicketStatus_stepOfDifferentDepartment_throwsFORBIDDEN")
    void updateTicketStatus_stepOfDifferentDepartment_throwsFORBIDDEN() {
        setupSecurityContext();

        Department otherDept = new Department();
        otherDept.setDepartmentId(UUID.randomUUID());

        User otherStaff = new User();
        otherStaff.setUserId(UUID.randomUUID());
        otherStaff.setDepartment(otherDept);

        TicketStep otherStep = new TicketStep();
        otherStep.setId(UUID.randomUUID());
        otherStep.setStatus(AssignmentStatus.ASSIGNED);
        otherStep.setAssignee(otherStaff);

        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setSteps(new ArrayList<>(List.of(otherStep)));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();
        request.setStepId(otherStep.getId());

        assertThatThrownBy(() -> staffTicketService.updateTicketStatus(ticketId, TicketStatus.IN_PROGRESS, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStatus_noStepInStaffDepartment_throwsFORBIDDEN")
    void updateTicketStatus_noStepInStaffDepartment_throwsFORBIDDEN() {
        setupSecurityContext();

        Department otherDept = new Department();
        otherDept.setDepartmentId(UUID.randomUUID());

        User otherStaff = new User();
        otherStaff.setUserId(UUID.randomUUID());
        otherStaff.setDepartment(otherDept);

        TicketStep otherStep = new TicketStep();
        otherStep.setId(UUID.randomUUID());
        otherStep.setStatus(AssignmentStatus.ASSIGNED);
        otherStep.setAssignee(otherStaff);

        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setSteps(new ArrayList<>(List.of(otherStep)));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();

        assertThatThrownBy(() -> staffTicketService.updateTicketStatus(ticketId, TicketStatus.IN_PROGRESS, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("resolveStep_midWorkflow_ticketRemainsInProgress")
    void resolveStep_midWorkflow_ticketRemainsInProgress() {
        setupSecurityContext();

        TicketStep step1 = new TicketStep();
        step1.setId(UUID.randomUUID());
        step1.setStatus(AssignmentStatus.IN_PROGRESS);
        step1.setAssignee(staffUser);
        step1.setTicket(ticket);

        Department otherDept = new Department();
        otherDept.setDepartmentId(UUID.randomUUID());
        User otherStaff = new User();
        otherStaff.setUserId(UUID.randomUUID());
        otherStaff.setDepartment(otherDept);

        TicketStep step2 = new TicketStep();
        step2.setId(UUID.randomUUID());
        step2.setStatus(AssignmentStatus.ASSIGNED);
        step2.setAssignee(otherStaff);
        step2.setTicket(ticket);

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setSteps(new ArrayList<>(List.of(step1, step2)));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(resolutionEvidenceRepository.existsByTicketStep_Id(step1.getId())).thenReturn(true);
        doAnswer(inv -> {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            return null;
        }).when(ticketWorkflowEngine).handleStepCompletion(step1.getId());

        when(ticketMapper.toTicketResponse(any())).thenAnswer(inv ->
                TicketResponse.builder().status(ticket.getStatus()).build()
        );

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();
        request.setStepId(step1.getId());

        TicketResponse response = staffTicketService.updateTicketStatus(ticketId, TicketStatus.RESOLVED, request);

        assertThat(response.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(step1.getStatus()).isEqualTo(AssignmentStatus.RESOLVED);
        verify(ticketWorkflowEngine).handleStepCompletion(step1.getId());
        verify(ticketRepository, never()).save(any());
        verify(ticketHistoryRepository).save(any());
    }

    @Test
    @DisplayName("resolveStep_finalStep_ticketTransitionToCompletedNotResolved")
    void resolveStep_finalStep_ticketTransitionToCompletedNotResolved() {
        setupSecurityContext();

        TicketStep step1 = new TicketStep();
        step1.setId(UUID.randomUUID());
        step1.setStatus(AssignmentStatus.RESOLVED);
        step1.setTicket(ticket);

        TicketStep step2 = new TicketStep();
        step2.setId(UUID.randomUUID());
        step2.setStatus(AssignmentStatus.IN_PROGRESS);
        step2.setAssignee(staffUser);
        step2.setTicket(ticket);

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setSteps(new ArrayList<>(List.of(step1, step2)));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(resolutionEvidenceRepository.existsByTicketStep_Id(step2.getId())).thenReturn(true);
        doAnswer(inv -> {
            ticket.setStatus(TicketStatus.COMPLETED);
            return null;
        }).when(ticketWorkflowEngine).handleStepCompletion(step2.getId());

        when(ticketMapper.toTicketResponse(any())).thenAnswer(inv ->
                TicketResponse.builder().status(ticket.getStatus()).build()
        );

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();
        request.setStepId(step2.getId());

        TicketResponse response = staffTicketService.updateTicketStatus(ticketId, TicketStatus.RESOLVED, request);

        assertThat(response.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        assertThat(step2.getStatus()).isEqualTo(AssignmentStatus.RESOLVED);
        verify(ticketWorkflowEngine).handleStepCompletion(step2.getId());
        verify(ticketRepository, never()).save(any());
        verify(ticketHistoryRepository).save(any());
    }

    @Test
    @DisplayName("updateTicketStatus_directComplete_whenStepNotResolved_throwsException")
    void updateTicketStatus_directComplete_whenStepNotResolved_throwsException() {
        setupSecurityContext();

        TicketStep step1 = new TicketStep();
        step1.setId(UUID.randomUUID());
        step1.setStatus(AssignmentStatus.RESOLVED);
        step1.setAssignee(staffUser);
        step1.setTicket(ticket);

        TicketStep step2 = new TicketStep();
        step2.setId(UUID.randomUUID());
        step2.setStatus(AssignmentStatus.IN_PROGRESS);
        step2.setAssignee(staffUser);
        step2.setTicket(ticket);

        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setSteps(new ArrayList<>(List.of(step1, step2)));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(resolutionEvidenceRepository.existsByTicketStep_Id(any())).thenReturn(true);

        UpdateStaffTicketStatusRequest request = new UpdateStaffTicketStatusRequest();

        assertThatThrownBy(() -> staffTicketService.updateTicketStatus(ticketId, TicketStatus.COMPLETED, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }
}
