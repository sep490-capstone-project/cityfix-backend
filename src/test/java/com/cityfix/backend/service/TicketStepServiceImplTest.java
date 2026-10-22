package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.response.TicketStepResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.impl.TicketStepServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketStepServiceImplTest {

    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.cityfix.backend.repository.ResolutionEvidenceRepository resolutionEvidenceRepository;
    @Mock
    private TicketStepMapper ticketStepMapper;
    @Mock
    private com.cityfix.backend.mapper.EvidenceMapper evidenceMapper;
    @Mock
    private com.cityfix.backend.mapper.TicketHistoryMapper ticketHistoryMapper;
    @Mock
    private com.cityfix.backend.repository.TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private TicketWorkflowEngine ticketWorkflowEngine;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private TicketStepServiceImpl ticketStepService;

    private User managerUser;
    private User adminUser;
    private User staffUser;
    private User otherStaffUser;
    private Department department;
    private Department otherDepartment;
    private TicketStep step;
    private UUID stepId;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setDepartmentId(UUID.randomUUID());

        otherDepartment = new Department();
        otherDepartment.setDepartmentId(UUID.randomUUID());

        Role managerRole = new Role(UUID.randomUUID(), "MANAGER");
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        Role staffRole = new Role(UUID.randomUUID(), "STAFF");

        managerUser = new User();
        managerUser.setUserId(UUID.randomUUID());
        managerUser.setEmail("manager@cityfix.com");
        managerUser.setRole(managerRole);

        adminUser = new User();
        adminUser.setUserId(UUID.randomUUID());
        adminUser.setEmail("admin@cityfix.com");
        adminUser.setRole(adminRole);

        staffUser = new User();
        staffUser.setUserId(UUID.randomUUID());
        staffUser.setEmail("staff@cityfix.com");
        staffUser.setRole(staffRole);
        staffUser.setDepartment(department);

        otherStaffUser = new User();
        otherStaffUser.setUserId(UUID.randomUUID());
        otherStaffUser.setEmail("otherstaff@cityfix.com");
        otherStaffUser.setRole(staffRole);
        otherStaffUser.setDepartment(otherDepartment);

        stepId = UUID.randomUUID();
        step = new TicketStep();
        step.setId(stepId);
        step.setStatus(AssignmentStatus.ASSIGNED);
        step.setAssignee(staffUser);
    }

    private void mockCurrentUser(User user) {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "password", Collections.emptyList())
        );
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("updateTicketStepStatus_whenCurrentUserIsAssignee_success")
    void updateTicketStepStatus_whenCurrentUserIsAssignee_success() {
        mockCurrentUser(staffUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.findByIdWithAssigneeAndDepartment(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketStepResponse mockResponse = TicketStepResponse.builder().id(stepId).status(AssignmentStatus.IN_PROGRESS).build();
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(mockResponse);

        TicketStepResponse response = ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.IN_PROGRESS);

        assertThat(response).isNotNull();
        assertThat(step.getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);
        assertThat(step.getStartedAt()).isNotNull();
        assertThat(step.getUpdatedAt()).isNotNull();
        verify(notificationService).notifyStepStarted(step, staffUser);
    }

    @Test
    @DisplayName("updateTicketStepStatus_whenCurrentUserIsAssignee_completed_setsCompletedAt")
    void updateTicketStepStatus_whenCurrentUserIsAssignee_completed_setsCompletedAt() {
        mockCurrentUser(staffUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketStepResponse mockResponse = TicketStepResponse.builder().id(stepId).status(AssignmentStatus.COMPLETED).build();
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(mockResponse);

        TicketStepResponse response = ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.COMPLETED);

        assertThat(response).isNotNull();
        assertThat(step.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(step.getCompletedAt()).isNotNull();
        assertThat(step.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateTicketStepStatus_resolved_withoutEvidence_throwsEvidenceRequired")
    void updateTicketStepStatus_resolved_withoutEvidence_throwsEvidenceRequired() {
        mockCurrentUser(staffUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(resolutionEvidenceRepository.existsByTicketStep_Id(stepId)).thenReturn(false);

        assertThatThrownBy(() -> ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.RESOLVED))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVIDENCE_REQUIRED);
    }

    @Test
    @DisplayName("updateTicketStepStatus_resolved_withEvidence_success")
    void updateTicketStepStatus_resolved_withEvidence_success() {
        mockCurrentUser(staffUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(resolutionEvidenceRepository.existsByTicketStep_Id(stepId)).thenReturn(true);
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketStepResponse mockResponse = TicketStepResponse.builder().id(stepId).status(AssignmentStatus.RESOLVED).build();
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(mockResponse);

        TicketStepResponse response = ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.RESOLVED);

        assertThat(response).isNotNull();
        assertThat(step.getStatus()).isEqualTo(AssignmentStatus.RESOLVED);
        assertThat(step.getCompletedAt()).isNotNull();
        verify(ticketWorkflowEngine).handleStepCompletion(stepId);
    }

    @Test
    @DisplayName("updateTicketStepStatus_whenCurrentUserIsNotAssignee_throwsForbidden")
    void updateTicketStepStatus_whenCurrentUserIsNotAssignee_throwsForbidden() {
        User colleagueStaff = new User();
        colleagueStaff.setUserId(UUID.randomUUID());
        colleagueStaff.setEmail("colleague@cityfix.com");
        colleagueStaff.setRole(new Role(UUID.randomUUID(), "STAFF"));
        colleagueStaff.setDepartment(department); // same department, but not assignee

        mockCurrentUser(colleagueStaff);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        assertThatThrownBy(() -> ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.IN_PROGRESS))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStepStatus_whenAssigneeIsNull_throwsForbidden")
    void updateTicketStepStatus_whenAssigneeIsNull_throwsForbidden() {
        mockCurrentUser(staffUser);
        step.setAssignee(null);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        assertThatThrownBy(() -> ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.IN_PROGRESS))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStepStatus_whenManager_throwsForbidden")
    void updateTicketStepStatus_whenManager_throwsForbidden() {
        mockCurrentUser(managerUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        assertThatThrownBy(() -> ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.IN_PROGRESS))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStepStatus_whenAdmin_throwsForbidden")
    void updateTicketStepStatus_whenAdmin_throwsForbidden() {
        mockCurrentUser(adminUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        assertThatThrownBy(() -> ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.IN_PROGRESS))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStepStatus_recalculatesTicketDeadline")
    void updateTicketStepStatus_recalculatesTicketDeadline() {
        mockCurrentUser(staffUser);

        Ticket ticket = new Ticket();
        ticket.setTicketId(UUID.randomUUID());
        ticket.setSubCategories(List.of(new TicketSubCategory(), new TicketSubCategory()));
        step.setTicket(ticket);
        java.time.LocalDateTime expectedDeadline = java.time.LocalDateTime.now().plusHours(48);

        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketService.calculateTicketDeadline(ticket)).thenReturn(expectedDeadline);

        TicketStepResponse mockResponse = TicketStepResponse.builder().id(stepId).status(AssignmentStatus.COMPLETED).build();
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(mockResponse);

        ticketStepService.updateTicketStepStatus(stepId, AssignmentStatus.COMPLETED);

        verify(ticketService).calculateTicketDeadline(ticket);
        verify(ticketRepository).save(ticket);
        assertThat(ticket.getDeadline()).isEqualTo(expectedDeadline);
    }

    @Test
    @DisplayName("getTicketStepsByTicketId_success")
    void getTicketStepsByTicketId_success() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(step));

        TicketStepResponse mockResponse = TicketStepResponse.builder().id(stepId).build();
        when(ticketStepMapper.toTicketStepResponseList(List.of(step))).thenReturn(List.of(mockResponse));

        List<TicketStepResponse> result = ticketStepService.getTicketStepsByTicketId(ticketId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(stepId);
    }

    @Test
    @DisplayName("getTicketStepsByTicketId_ticketNotFound_throwsAppException")
    void getTicketStepsByTicketId_ticketNotFound_throwsAppException() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsById(ticketId)).thenReturn(false);

        assertThatThrownBy(() -> ticketStepService.getTicketStepsByTicketId(ticketId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("deleteTicketStep_whenCurrentUserIsManager_success")
    void deleteTicketStep_whenCurrentUserIsManager_success() {
        mockCurrentUser(managerUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        ticketStepService.deleteTicketStep(stepId);

        verify(ticketStepRepository).delete(step);
    }

    @Test
    @DisplayName("deleteTicketStep_whenCurrentUserIsNotManager_throwsForbidden")
    void deleteTicketStep_whenCurrentUserIsNotManager_throwsForbidden() {
        mockCurrentUser(staffUser);

        assertThatThrownBy(() -> ticketStepService.deleteTicketStep(stepId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(ticketStepRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTicketStep_stepNotFound_throwsAppException")
    void deleteTicketStep_stepNotFound_throwsAppException() {
        mockCurrentUser(managerUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketStepService.deleteTicketStep(stepId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STEP_NOT_FOUND);
    }

    @Test
    @DisplayName("cascadeCancelTicketSteps_cancelsActiveStepsAndSetsTimestamps")
    void cascadeCancelTicketSteps_cancelsActiveStepsAndSetsTimestamps() {
        UUID ticketId = UUID.randomUUID();
        TicketStep openStep = new TicketStep();
        openStep.setId(UUID.randomUUID());
        openStep.setStatus(AssignmentStatus.ASSIGNED);

        TicketStep inProgressStep = new TicketStep();
        inProgressStep.setId(UUID.randomUUID());
        inProgressStep.setStatus(AssignmentStatus.IN_PROGRESS);

        TicketStep alreadyCancelledStep = new TicketStep();
        alreadyCancelledStep.setId(UUID.randomUUID());
        alreadyCancelledStep.setStatus(AssignmentStatus.CANCELLED);

        when(ticketStepRepository.findByTicketTicketId(ticketId))
                .thenReturn(List.of(openStep, inProgressStep, alreadyCancelledStep));

        ticketStepService.cascadeCancelTicketSteps(ticketId);

        assertThat(openStep.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(openStep.getCompletedAt()).isNotNull();
        assertThat(inProgressStep.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(inProgressStep.getCompletedAt()).isNotNull();

        verify(ticketStepRepository).saveAll(argThat(iterable -> {
            List<TicketStep> saved = (List<TicketStep>) iterable;
            return saved.size() == 2;
        }));
    }

    @Test
    @DisplayName("submitEvidence_whenCurrentUserIsAssignee_success")
    void submitEvidence_whenCurrentUserIsAssignee_success() {
        mockCurrentUser(staffUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        List<org.springframework.web.multipart.MultipartFile> files = List.of(file);
        com.cityfix.backend.model.dto.response.ImageUploadResponse uploadRes =
                com.cityfix.backend.model.dto.response.ImageUploadResponse.builder().secureUrl("https://image.url/pic.jpg").build();
        when(ticketService.uploadImage(files, "cityfix/evidences")).thenReturn(List.of(uploadRes));

        ResolutionEvidence evidence = ResolutionEvidence.builder()
                .evidenceId(UUID.randomUUID())
                .ticketStep(step)
                .staff(staffUser)
                .imageUrl("https://image.url/pic.jpg")
                .resolutionNote("Đã xử lý xong")
                .build();
        when(evidenceMapper.toResolutionEvidence(eq(step), eq(staffUser), eq("https://image.url/pic.jpg"), eq("Đã xử lý xong")))
                .thenReturn(evidence);
        when(resolutionEvidenceRepository.saveAll(anyList())).thenReturn(List.of(evidence));

        com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse evidenceRes =
                com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse.builder()
                        .evidenceId(evidence.getEvidenceId())
                        .imageUrl("https://image.url/pic.jpg")
                        .resolutionNote("Đã xử lý xong")
                        .build();
        when(evidenceMapper.toResponseList(List.of(evidence))).thenReturn(List.of(evidenceRes));

        List<com.cityfix.backend.model.dto.response.ResolutionEvidenceResponse> result =
                ticketStepService.submitEvidence(stepId, files, "Đã xử lý xong");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://image.url/pic.jpg");
        verify(resolutionEvidenceRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("submitEvidence_whenCurrentUserIsNotAssignee_throwsForbidden")
    void submitEvidence_whenCurrentUserIsNotAssignee_throwsForbidden() {
        mockCurrentUser(otherStaffUser);
        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        List<org.springframework.web.multipart.MultipartFile> files = List.of(file);

        assertThatThrownBy(() -> ticketStepService.submitEvidence(stepId, files, "Ghi chú"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(ticketService, never()).uploadImage(any(), any());
        verify(resolutionEvidenceRepository, never()).saveAll(any());
    }
}
