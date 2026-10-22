package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.response.ManagerDepartmentResponse;
import com.cityfix.backend.model.dto.response.SuggestedAssigneeResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.impl.ManagerTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.cityfix.backend.model.dto.request.ReviewTicketRequest;
import com.cityfix.backend.model.dto.request.UpdateTicketStepDependenciesRequest;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerTicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private TicketStepMapper ticketStepMapper;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private TicketHistoryMapper ticketHistoryMapper;
    @Mock
    private TicketService ticketService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private RoutingRuleRepository routingRuleRepository;
    @Mock
    private StepRepository stepRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SubCategoryRepository subCategoryRepository;
    @Mock
    private UserDepartmentRepository userDepartmentRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;
    @Mock
    private TicketWorkflowEngine ticketWorkflowEngine;
    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private ManagerTicketServiceImpl managerTicketService;

    private User managerUser;
    private User staffUser;
    private Ticket ticket;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        Role managerRole = new Role(UUID.randomUUID(), "MANAGER");
        Role staffRole = new Role(UUID.randomUUID(), "STAFF");

        managerUser = new User();
        managerUser.setUserId(UUID.randomUUID());
        managerUser.setEmail("manager@cityfix.com");
        managerUser.setRole(managerRole);

        staffUser = new User();
        staffUser.setUserId(UUID.randomUUID());
        staffUser.setEmail("staff@cityfix.com");
        staffUser.setRole(staffRole);

        ticketId = UUID.randomUUID();
        ticket = new Ticket();
        ticket.setTicketId(ticketId);
    }

    private void mockCurrentUser(User user) {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "password", Collections.emptyList())
        );
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("updateTicketStepDeadline_byManager_cascadesSubsequentStepsAndUpdatesTicketDeadline")
    void updateTicketStepDeadline_byManager_cascadesSubsequentStepsAndUpdatesTicketDeadline() {
        mockCurrentUser(managerUser);

        UUID stepId1 = UUID.randomUUID();
        UUID stepId2 = UUID.randomUUID();

        Step s1 = Step.builder().stepId(UUID.randomUUID()).duration(6).build();
        Step s2 = Step.builder().stepId(UUID.randomUUID()).duration(12).build();

        TicketStep ts1 = TicketStep.builder()
                .id(stepId1)
                .ticket(ticket)
                .step(s1)
                .createdAt(LocalDateTime.now())
                .status(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED)
                .deadline(LocalDateTime.now().plusHours(6))
                .build();

        TicketStep ts2 = TicketStep.builder()
                .id(stepId2)
                .ticket(ticket)
                .step(s2)
                .createdAt(LocalDateTime.now().plusSeconds(1))
                .status(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED)
                .startedAt(LocalDateTime.now().plusHours(6))
                .deadline(LocalDateTime.now().plusHours(18))
                .build();

        LocalDateTime newDeadline1 = LocalDateTime.now().plusHours(24);
        LocalDateTime expectedDeadline2 = newDeadline1.plusHours(12);

        when(ticketStepRepository.findById(stepId1)).thenReturn(Optional.of(ts1));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of(ts1, ts2));
        when(ticketStepRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId1).deadline(newDeadline1).build()
        );

        com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest request =
                new com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest(newDeadline1);

        managerTicketService.updateTicketStepDeadline(stepId1, request);

        assertThat(ts1.getDeadline()).isEqualTo(newDeadline1);
        assertThat(ts2.getStartedAt()).isEqualTo(newDeadline1);
        assertThat(ts2.getDeadline()).isEqualTo(expectedDeadline2);
        assertThat(ts2.getUpdatedAt()).isNotNull();
        assertThat(ticket.getDeadline()).isEqualTo(expectedDeadline2);
    }

    @Test
    @DisplayName("rejectInitiatedTicket_cascadesCancelSteps_andSetsTimestamps")
    void rejectInitiatedTicket_cascadesCancelSteps_andSetsTimestamps() {
        mockCurrentUser(managerUser);

        ticket.setStatus(com.cityfix.backend.model.enums.TicketStatus.INITIATED);

        TicketStep activeStep = new TicketStep();
        activeStep.setId(UUID.randomUUID());
        activeStep.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(activeStep));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(TicketResponse.builder().id(ticketId).build());

        managerTicketService.rejectInitiatedTicket(ticketId, "Không hợp lệ");

        assertThat(ticket.getStatus()).isEqualTo(com.cityfix.backend.model.enums.TicketStatus.REJECTED);
        assertThat(activeStep.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.CANCELLED);
        assertThat(activeStep.getCompletedAt()).isNotNull();
        verify(ticketStepRepository).saveAll(argThat(iterable -> {
            List<TicketStep> steps = (List<TicketStep>) iterable;
            return steps.size() == 1 && steps.get(0).getStatus() == com.cityfix.backend.model.enums.AssignmentStatus.CANCELLED;
        }));
    }

    @Test
    @DisplayName("reviewTicket_byNonManager_throwsForbidden")
    void reviewTicket_byNonManager_throwsForbidden() {
        mockCurrentUser(staffUser);

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = new com.cityfix.backend.model.dto.request.ReviewTicketRequest();

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("reviewTicket_byManager_validAllInOnePayload_success")
    void reviewTicket_byManager_validAllInOnePayload_success() {
        mockCurrentUser(managerUser);

        ticket.setStatus(TicketStatus.INITIATED);
        ticket.setNeedsManualReview(true);
        ticket.setUser(managerUser);

        UUID subCatId = UUID.randomUUID();
        SubCategory mockSubCat = SubCategory.builder()
                .subCategoryId(subCatId)
                .name("Sự cố sạt lở")
                .isActive(true)
                .sla(24)
                .build();

        UUID stepId = UUID.randomUUID();
        Step mockStep = Step.builder()
                .stepId(stepId)
                .stepName("Khảo sát hiện trường")
                .build();

        Department dept = Department.builder().departmentId(UUID.randomUUID()).name("Phòng QLĐT").build();
        User staffInDept = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Nguyễn Văn A")
                .status(com.cityfix.backend.model.enums.UserStatus.ACTIVE)
                .department(dept)
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(subCategoryRepository.findAllById(List.of(subCatId))).thenReturn(List.of(mockSubCat));
        when(stepRepository.findAllById(any())).thenReturn(List.of(mockStep));
        when(userRepository.findAllById(any())).thenReturn(List.of(staffInDept));
        when(ticketService.calculateTicketDeadline(any(Ticket.class))).thenReturn(LocalDateTime.now().plusHours(12));
        when(ticketStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketHistory mockHistory = TicketHistory.builder()
                .actionType(com.cityfix.backend.model.enums.ActionType.MANUAL_APPROVED)
                .currentStatus(TicketStatus.IN_PROGRESS)
                .build();
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any())).thenReturn(mockHistory);

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .isLifeThreatening(true)
                .reviewNote("Duyệt khẩn cấp")
                .subCategoryIds(List.of(subCatId))
                .steps(List.of(com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(stepId)
                        .assigneeId(staffInDept.getUserId())
                        .deadline(LocalDateTime.now().plusHours(12))
                        .notes("Giao khảo sát")
                        .build()))
                .build();

        TicketResponse response = managerTicketService.reviewTicket(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getNeedsManualReview()).isFalse();
        assertThat(ticket.getIsLifeThreatening()).isTrue();
        assertThat(ticket.getReviewNote()).isEqualTo("Duyệt khẩn cấp");
        assertThat(ticket.getSubCategories()).hasSize(1);
        assertThat(ticket.getSteps()).hasSize(1);
        assertThat(ticket.getSteps().get(0).getAssignee()).isEqualTo(staffInDept);
        assertThat(ticket.getSteps().get(0).getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS);

        verify(notificationService).createAndSendNotification(eq(ticket), any(), any(), any());
        verify(ticketHistoryRepository, atLeastOnce()).save(argThat(h ->
                h != null
                        && h.getActionType() == com.cityfix.backend.model.enums.ActionType.MANUAL_APPROVED
                        && h.getCurrentStatus() == TicketStatus.IN_PROGRESS
        ));
    }

    @Test
    @DisplayName("reviewTicket_ticketNotInitiated_throwsTICKET_NOT_INITIATED")
    void reviewTicket_ticketNotInitiated_throwsTICKET_NOT_INITIATED() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.ASSIGNED); // Not INITIATED

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = new com.cityfix.backend.model.dto.request.ReviewTicketRequest();

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_INITIATED);
    }

    @Test
    @DisplayName("reviewTicket_emptySubCategories_throwsINVALID_REQUEST")
    void reviewTicket_emptySubCategories_throwsINVALID_REQUEST() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .subCategoryIds(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("reviewTicket_duplicateStepId_throwsINVALID_REQUEST")
    void reviewTicket_duplicateStepId_throwsINVALID_REQUEST() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        UUID subCatId = UUID.randomUUID();
        SubCategory mockSubCat = SubCategory.builder()
                .subCategoryId(subCatId)
                .isActive(true)
                .build();

        UUID stepId = UUID.randomUUID();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(subCategoryRepository.findAllById(List.of(subCatId))).thenReturn(List.of(mockSubCat));

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .subCategoryIds(List.of(subCatId))
                .steps(List.of(
                        com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                                .stepId(stepId).assigneeId(UUID.randomUUID()).build(),
                        com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                                .stepId(stepId).assigneeId(UUID.randomUUID()).build()
                ))
                .build();

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("reviewTicket_pastDeadline_throwsINVALID_REQUEST")
    void reviewTicket_pastDeadline_throwsINVALID_REQUEST() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        UUID subCatId = UUID.randomUUID();
        SubCategory mockSubCat = SubCategory.builder()
                .subCategoryId(subCatId)
                .isActive(true)
                .build();

        UUID stepId = UUID.randomUUID();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(subCategoryRepository.findAllById(List.of(subCatId))).thenReturn(List.of(mockSubCat));

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .subCategoryIds(List.of(subCatId))
                .steps(List.of(
                        com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                                .stepId(stepId)
                                .assigneeId(UUID.randomUUID())
                                .deadline(LocalDateTime.now().minusDays(1)) // Past deadline
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("rejectInitiatedTicket_byNonManager_throwsForbidden")
    void rejectInitiatedTicket_byNonManager_throwsForbidden() {
        mockCurrentUser(staffUser);

        assertThatThrownBy(() -> managerTicketService.rejectInitiatedTicket(ticketId, "Lý do"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getDepartmentTickets_asManager_withExplicitDepartment_success")
    void getDepartmentTickets_asManager_withExplicitDepartment_success() {
        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().departmentId(deptId).name("Test Dept").build();
        managerUser.setDepartment(dept);
        mockCurrentUser(managerUser);

        Pageable pageable = PageRequest.of(0, 10);
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        Page<Ticket> ticketPage = new PageImpl<>(List.of(t));

        when(ticketRepository.findTicketsByDepartmentAndCriteria(deptId, SeverityLevel.HIGH, Priority.HIGH, TicketStatus.IN_PROGRESS, pageable))
                .thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(t)).thenReturn(TicketResponse.builder().id(t.getTicketId()).build());
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(dept));

        Page<TicketResponse> result = managerTicketService.getDepartmentTickets(
                deptId, SeverityLevel.HIGH, Priority.HIGH, TicketStatus.IN_PROGRESS, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(t.getTicketId());
    }

    @Test
    @DisplayName("getDepartmentTickets_asManager_withNoExplicitDepartment_usesManagerDepartment")
    void getDepartmentTickets_asManager_withNoExplicitDepartment_usesManagerDepartment() {
        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().departmentId(deptId).name("Test Dept").build();
        managerUser.setDepartment(dept);
        mockCurrentUser(managerUser);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Ticket> emptyPage = new PageImpl<>(Collections.emptyList());

        when(ticketRepository.findTicketsByDepartmentAndCriteria(deptId, null, null, null, pageable))
                .thenReturn(emptyPage);

        Page<TicketResponse> result = managerTicketService.getDepartmentTickets(
                null, null, null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(ticketRepository).findTicketsByDepartmentAndCriteria(deptId, null, null, null, pageable);
    }

    @Test
    @DisplayName("getDepartmentTickets_asManager_noDepartmentAndNoExplicitParam_throwsUserNotInDepartment")
    void getDepartmentTickets_asManager_noDepartmentAndNoExplicitParam_throwsUserNotInDepartment() {
        managerUser.setDepartment(null);
        mockCurrentUser(managerUser);

        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> managerTicketService.getDepartmentTickets(null, null, null, null, pageable))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_IN_DEPARTMENT);
    }

    @Test
    @DisplayName("getDepartmentTickets_asNonManager_throwsForbidden")
    void getDepartmentTickets_asNonManager_throwsForbidden() {
        mockCurrentUser(staffUser);

        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> managerTicketService.getDepartmentTickets(UUID.randomUUID(), null, null, null, pageable))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getDepartmentTickets_asManager_passingOtherDepartment_throwsForbidden")
    void getDepartmentTickets_asManager_passingOtherDepartment_throwsForbidden() {
        UUID myDeptId = UUID.randomUUID();
        UUID otherDeptId = UUID.randomUUID();

        Department myDept = new Department();
        myDept.setDepartmentId(myDeptId);
        managerUser.setDepartment(myDept);
        mockCurrentUser(managerUser);

        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> managerTicketService.getDepartmentTickets(otherDeptId, null, null, null, pageable))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getDepartmentTickets_asManager_passingOwnDepartment_success")
    void getDepartmentTickets_asManager_passingOwnDepartment_success() {
        UUID myDeptId = UUID.randomUUID();
        Department myDept = new Department();
        myDept.setDepartmentId(myDeptId);
        myDept.setName("Phòng QLĐT");
        managerUser.setDepartment(myDept);
        mockCurrentUser(managerUser);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Ticket> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(ticketRepository.findTicketsByDepartmentAndCriteria(myDeptId, null, null, null, pageable))
                .thenReturn(emptyPage);
        when(departmentRepository.findById(myDeptId)).thenReturn(Optional.of(myDept));

        Page<TicketResponse> result = managerTicketService.getDepartmentTickets(
                myDeptId, null, null, null, pageable);

        assertThat(result).isNotNull();
        verify(ticketRepository).findTicketsByDepartmentAndCriteria(myDeptId, null, null, null, pageable);
    }

    @Test
    @DisplayName("getDepartmentTickets_asAdmin_passingAnyDepartment_success")
    void getDepartmentTickets_asAdmin_passingAnyDepartment_success() {
        User adminUser = new User();
        adminUser.setUserId(UUID.randomUUID());
        adminUser.setEmail("admin@cityfix.com");
        adminUser.setRole(new Role(UUID.randomUUID(), "ADMIN"));
        mockCurrentUser(adminUser);

        UUID anyDeptId = UUID.randomUUID();
        Department anyDept = new Department();
        anyDept.setDepartmentId(anyDeptId);
        anyDept.setName("Sở GTVT");

        Pageable pageable = PageRequest.of(0, 10);
        Page<Ticket> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(ticketRepository.findTicketsByDepartmentAndCriteria(anyDeptId, null, null, null, pageable))
                .thenReturn(emptyPage);
        when(departmentRepository.findById(anyDeptId)).thenReturn(Optional.of(anyDept));

        Page<TicketResponse> result = managerTicketService.getDepartmentTickets(
                anyDeptId, null, null, null, pageable);

        assertThat(result).isNotNull();
        verify(ticketRepository).findTicketsByDepartmentAndCriteria(anyDeptId, null, null, null, pageable);
    }

    @Test
    @DisplayName("getManagerDepartments_asManager_returnsDepartmentsWithStaff")
    void getManagerDepartments_asManager_returnsDepartmentsWithStaff() {
        mockCurrentUser(managerUser);

        Department dept = Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Phòng Quản lý Đô thị")
                .level(com.cityfix.backend.model.enums.DepartmentLevel.WARD)
                .contactEmail("dept@cityfix.vn")
                .phoneNumber("0281234567")
                .build();

        User staff = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Trần Cán Bộ")
                .phoneNumber("0912345678")
                .email("staff@cityfix.vn")
                .department(dept)
                .build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerUser.getUserId()))
                .thenReturn(List.of(dept));
        when(userDepartmentRepository.findActiveStaffByDepartmentId(dept.getDepartmentId()))
                .thenReturn(List.of(staff));

        List<ManagerDepartmentResponse> result = managerTicketService.getManagerDepartments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDepartmentId()).isEqualTo(dept.getDepartmentId());
        assertThat(result.get(0).getDepartmentName()).isEqualTo("Phòng Quản lý Đô thị");
        assertThat(result.get(0).getStaffs()).hasSize(1);
        assertThat(result.get(0).getStaffs().get(0).getUserId()).isEqualTo(staff.getUserId());
        assertThat(result.get(0).getStaffs().get(0).getFullName()).isEqualTo("Trần Cán Bộ");
        assertThat(result.get(0).getStaffs().get(0).getUsername()).isEqualTo("staff@cityfix.vn");
        assertThat(result.get(0).getStaffs().get(0).getEmail()).isEqualTo("staff@cityfix.vn");
    }

    @Test
    @DisplayName("getManagerDepartments_asStaff_throwsForbidden")
    void getManagerDepartments_asStaff_throwsForbidden() {
        mockCurrentUser(staffUser);

        assertThatThrownBy(() -> managerTicketService.getManagerDepartments())
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("reviewTicket_nullDeadline_setsDefaultDeadlineFromStepDuration")
    void reviewTicket_nullDeadline_setsDefaultDeadlineFromStepDuration() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        UUID subCatId = UUID.randomUUID();
        SubCategory mockSubCat = SubCategory.builder()
                .subCategoryId(subCatId)
                .isActive(true)
                .build();

        UUID stepId = UUID.randomUUID();
        Step mockStep = Step.builder()
                .stepId(stepId)
                .stepName("Khảo sát")
                .duration(6)
                .build();

        Department dept = Department.builder().departmentId(UUID.randomUUID()).build();
        User staffInDept = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Lê Nhân Viên")
                .status(com.cityfix.backend.model.enums.UserStatus.ACTIVE)
                .department(dept)
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(subCategoryRepository.findAllById(List.of(subCatId))).thenReturn(List.of(mockSubCat));
        when(stepRepository.findAllById(any())).thenReturn(List.of(mockStep));
        when(userRepository.findAllById(any())).thenReturn(List.of(staffInDept));
        when(ticketStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketHistory mockHistory = TicketHistory.builder()
                .actionType(com.cityfix.backend.model.enums.ActionType.MANUAL_APPROVED)
                .currentStatus(TicketStatus.IN_PROGRESS)
                .build();
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any())).thenReturn(mockHistory);

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .subCategoryIds(List.of(subCatId))
                .steps(List.of(com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(stepId)
                        .assigneeId(staffInDept.getUserId())
                        .deadline(null) // Không truyền deadline
                        .build()))
                .build();

        LocalDateTime before = LocalDateTime.now();
        TicketResponse response = managerTicketService.reviewTicket(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(ticket.getSteps()).hasSize(1);
        assertThat(ticket.getSteps().get(0).getStartedAt()).isNotNull();
        assertThat(ticket.getSteps().get(0).getDeadline()).isNotNull();
        assertThat(ticket.getSteps().get(0).getDeadline()).isAfterOrEqualTo(before.plusHours(6).minusSeconds(5));
        assertThat(ticket.getDeadline()).isEqualTo(ticket.getSteps().get(0).getDeadline());
    }

    @Test
    @DisplayName("reviewTicket_multipleSteps_dagDependenciesAndAnchorActivation")
    void reviewTicket_multipleSteps_dagDependenciesAndAnchorActivation() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        UUID subCatId = UUID.randomUUID();
        SubCategory mockSubCat = SubCategory.builder()
                .subCategoryId(subCatId)
                .isActive(true)
                .build();

        UUID stepId1 = UUID.randomUUID();
        Step mockStep1 = Step.builder()
                .stepId(stepId1)
                .stepName("Khảo sát")
                .duration(6)
                .build();

        UUID stepId2 = UUID.randomUUID();
        Step mockStep2 = Step.builder()
                .stepId(stepId2)
                .stepName("Thi công")
                .duration(12)
                .build();

        Department dept = Department.builder().departmentId(UUID.randomUUID()).build();
        User staffInDept = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Lê Nhân Viên")
                .status(com.cityfix.backend.model.enums.UserStatus.ACTIVE)
                .department(dept)
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(subCategoryRepository.findAllById(List.of(subCatId))).thenReturn(List.of(mockSubCat));
        when(stepRepository.findAllById(any())).thenReturn(List.of(mockStep1, mockStep2));
        when(userRepository.findAllById(any())).thenReturn(List.of(staffInDept));
        when(ticketStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketHistory mockHistory = TicketHistory.builder()
                .actionType(com.cityfix.backend.model.enums.ActionType.MANUAL_APPROVED)
                .currentStatus(TicketStatus.IN_PROGRESS)
                .build();
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any())).thenReturn(mockHistory);

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .subCategoryIds(List.of(subCatId))
                .steps(List.of(
                        com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                                .stepId(stepId1)
                                .assigneeId(staffInDept.getUserId())
                                .dependsOnStepIds(Collections.emptyList())
                                .deadline(null)
                                .build(),
                        com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                                .stepId(stepId2)
                                .assigneeId(staffInDept.getUserId())
                                .dependsOnStepIds(List.of(stepId1))
                                .deadline(null)
                                .build()
                ))
                .build();

        managerTicketService.reviewTicket(ticketId, request);

        assertThat(ticket.getSteps()).hasSize(2);
        TicketStep ts1 = ticket.getSteps().get(0);
        TicketStep ts2 = ticket.getSteps().get(1);
        assertThat(ts1.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS);
        assertThat(ts1.getDeadline()).isEqualTo(ts1.getStartedAt().plusHours(6));
        assertThat(ts2.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);
        assertThat(ts2.getDependsOn()).contains(ts1);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("getManagerAssignedDepartmentTickets_asManager_returnsFullTicketAndAllSteps")
    void getManagerAssignedDepartmentTickets_asManager_returnsFullTicketAndAllSteps() {
        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().departmentId(deptId).name("Phòng Quản lý Đô thị").build();
        mockCurrentUser(managerUser);

        LocalDateTime now = LocalDateTime.now();
        TicketStep step2 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).createdAt(now.plusSeconds(1)).build();
        TicketStep step1 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).createdAt(now).build();
        ticket.setSteps(List.of(step2, step1));

        com.cityfix.backend.model.dto.response.TicketStepResponse stepRes1 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(step1.getId()).build();
        com.cityfix.backend.model.dto.response.TicketStepResponse stepRes2 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(step2.getId()).build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<Ticket> ticketPage = new PageImpl<>(List.of(ticket), pageable, 1);

        when(userDepartmentRepository.findDepartmentsByUserId(managerUser.getUserId())).thenReturn(List.of(dept));
        when(ticketRepository.findTicketsByAssigneeDepartmentIds(any(), eq(pageable))).thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(TicketResponse.builder().id(ticketId).description("Sự cố ngập nước").build());
        when(ticketStepMapper.toTicketStepResponseList(List.of(step1, step2))).thenReturn(List.of(stepRes1, stepRes2));

        Page<TicketResponse> result = managerTicketService.getManagerAssignedDepartmentTickets(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        TicketResponse res = result.getContent().get(0);
        assertThat(res.getId()).isEqualTo(ticketId);
        assertThat(res.getSteps()).hasSize(2);
        assertThat(res.getSteps().get(0).getId()).isEqualTo(step1.getId());
        assertThat(res.getSteps().get(1).getId()).isEqualTo(step2.getId());
    }

    @Test
    @DisplayName("getManagerAssignedDepartmentTickets_asStaff_throwsForbidden")
    void getManagerAssignedDepartmentTickets_asStaff_throwsForbidden() {
        mockCurrentUser(staffUser);

        assertThatThrownBy(() -> managerTicketService.getManagerAssignedDepartmentTickets(null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getManagerAssignedDepartmentTickets_unmanagedDepartmentId_throwsForbidden")
    void getManagerAssignedDepartmentTickets_unmanagedDepartmentId_throwsForbidden() {
        Department dept = Department.builder().departmentId(UUID.randomUUID()).name("Phòng A").build();
        mockCurrentUser(managerUser);
        when(userDepartmentRepository.findDepartmentsByUserId(managerUser.getUserId())).thenReturn(List.of(dept));

        assertThatThrownBy(() -> managerTicketService.getManagerAssignedDepartmentTickets(UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getResolvedTickets_asManager_returnsResolvedTicketsWithSteps")
    void getResolvedTickets_asManager_returnsResolvedTicketsWithSteps() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.RESOLVED);

        TicketStep step1 = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .status(com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED)
                .build();
        ticket.setSteps(List.of(step1));

        com.cityfix.backend.model.dto.response.TicketStepResponse stepRes1 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder()
                        .id(step1.getId())
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED)
                        .build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<Ticket> ticketPage = new PageImpl<>(List.of(ticket), pageable, 1);

        when(ticketRepository.findResolvedTicketsWithAllStepsResolved(pageable)).thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(
                TicketResponse.builder().id(ticketId).status(TicketStatus.RESOLVED).build()
        );
        when(ticketStepMapper.toTicketStepResponseList(List.of(step1))).thenReturn(List.of(stepRes1));

        Page<TicketResponse> result = managerTicketService.getResolvedTickets(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ticketId);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(result.getContent().get(0).getSteps()).hasSize(1);
        assertThat(result.getContent().get(0).getSteps().get(0).getStatus())
                .isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED);
    }

    @Test
    @DisplayName("updateResolvedTicketStepStatus_toCompleted_triggersWorkflowEngine")
    void updateResolvedTicketStepStatus_toCompleted_triggersWorkflowEngine() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.RESOLVED);

        UUID stepId = UUID.randomUUID();
        TicketStep step = TicketStep.builder()
                .id(stepId)
                .ticket(ticket)
                .status(com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED)
                .build();

        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketStepMapper.toTicketStepResponse(step)).thenReturn(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder()
                        .id(stepId)
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED)
                        .build()
        );

        com.cityfix.backend.model.dto.response.TicketStepResponse response =
                managerTicketService.updateResolvedTicketStepStatus(
                        stepId, com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED);

        assertThat(response.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED);
        assertThat(step.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED);
        assertThat(step.getCompletedAt()).isNotNull();
        verify(ticketWorkflowEngine).handleStepCompletion(stepId);
    }

    @Test
    @DisplayName("updateResolvedTicketStepStatus_toInProgress_revertsStepAndTicketToInProgress")
    void updateResolvedTicketStepStatus_toInProgress_revertsStepAndTicketToInProgress() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.RESOLVED);

        UUID stepId = UUID.randomUUID();
        TicketStep step = TicketStep.builder()
                .id(stepId)
                .ticket(ticket)
                .status(com.cityfix.backend.model.enums.AssignmentStatus.RESOLVED)
                .completedAt(LocalDateTime.now())
                .build();

        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());
        when(ticketStepMapper.toTicketStepResponse(step)).thenReturn(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder()
                        .id(stepId)
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS)
                        .build()
        );

        com.cityfix.backend.model.dto.response.TicketStepResponse response =
                managerTicketService.updateResolvedTicketStepStatus(
                        stepId, com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS);

        assertThat(response.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS);
        assertThat(step.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS);
        assertThat(step.getCompletedAt()).isNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(ticketRepository).save(ticket);
        verify(ticketHistoryRepository).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("updateResolvedTicketStepStatus_whenStepNotResolved_throwsInvalidStatusTransition")
    void updateResolvedTicketStepStatus_whenStepNotResolved_throwsInvalidStatusTransition() {
        mockCurrentUser(managerUser);
        UUID stepId = UUID.randomUUID();
        TicketStep step = TicketStep.builder()
                .id(stepId)
                .ticket(ticket)
                .status(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED)
                .build();

        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));

        assertThatThrownBy(() -> managerTicketService.updateResolvedTicketStepStatus(
                stepId, com.cityfix.backend.model.enums.AssignmentStatus.COMPLETED))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("reviewTicket_twoPhaseDag_activatesAnchorStepsAndSetsTicketInProgress")
    void reviewTicket_twoPhaseDag_activatesAnchorStepsAndSetsTicketInProgress() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        SubCategory sc = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .name("Ổ gà đường phố")
                .isActive(true)
                .build();
        when(subCategoryRepository.findAllById(any())).thenReturn(List.of(sc));

        Step s1 = Step.builder().stepId(UUID.randomUUID()).stepName("Khảo sát").duration(2).build();
        Step s2 = Step.builder().stepId(UUID.randomUUID()).stepName("Trám vá").duration(4).build();
        when(stepRepository.findAllById(any())).thenReturn(List.of(s1, s2));

        staffUser.setStatus(com.cityfix.backend.model.enums.UserStatus.ACTIVE);
        staffUser.setDepartment(new Department());
        when(userRepository.findAllById(any())).thenReturn(List.of(staffUser));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketStepRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<TicketStep> steps = inv.getArgument(0);
            for (TicketStep st : steps) {
                if (st.getId() == null) st.setId(UUID.randomUUID());
            }
            return steps;
        });
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TicketHistory());
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(
                TicketResponse.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build()
        );

        ReviewTicketRequest request = new ReviewTicketRequest();
        request.setSubCategoryIds(List.of(sc.getSubCategoryId()));
        request.setSteps(List.of(
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s1.getStepId())
                        .assigneeId(staffUser.getUserId())
                        .dependsOnStepIds(Collections.emptyList())
                        .build(),
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s2.getStepId())
                        .assigneeId(staffUser.getUserId())
                        .dependsOnStepIds(List.of(s1.getStepId()))
                        .build()
        ));

        TicketResponse response = managerTicketService.reviewTicket(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        // Kiểm tra bước 1 (Anchor) được kích hoạt IN_PROGRESS
        TicketStep anchorStep = ticket.getSteps().stream()
                .filter(ts -> ts.getStep().getStepId().equals(s1.getStepId()))
                .findFirst().orElseThrow();
        assertThat(anchorStep.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS);
        assertThat(anchorStep.getStartedAt()).isNotNull();
        assertThat(anchorStep.getDeadline()).isNotNull();
        verify(notificationService).notifyStepStarted(eq(anchorStep), eq(managerUser));

        // Kiểm tra bước 2 ở trạng thái ASSIGNED và phụ thuộc vào bước 1
        TicketStep dependentStep = ticket.getSteps().stream()
                .filter(ts -> ts.getStep().getStepId().equals(s2.getStepId()))
                .findFirst().orElseThrow();
        assertThat(dependentStep.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);
        assertThat(dependentStep.getDependsOn()).contains(anchorStep);
    }

    @Test
    @DisplayName("reviewTicket_whenDagHasCycle_throwsStepCycleDetected")
    void reviewTicket_whenDagHasCycle_throwsStepCycleDetected() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        SubCategory sc = SubCategory.builder().subCategoryId(UUID.randomUUID()).isActive(true).build();
        when(subCategoryRepository.findAllById(any())).thenReturn(List.of(sc));

        Step s0 = Step.builder().stepId(UUID.randomUUID()).build();
        Step s1 = Step.builder().stepId(UUID.randomUUID()).build();
        Step s2 = Step.builder().stepId(UUID.randomUUID()).build();
        when(stepRepository.findAllById(any())).thenReturn(List.of(s0, s1, s2));

        staffUser.setStatus(com.cityfix.backend.model.enums.UserStatus.ACTIVE);
        staffUser.setDepartment(new Department());
        when(userRepository.findAllById(any())).thenReturn(List.of(staffUser));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ReviewTicketRequest request = new ReviewTicketRequest();
        request.setSubCategoryIds(List.of(sc.getSubCategoryId()));
        // s0 is anchor, s1 depends on s2, s2 depends on s1 (cycle)
        request.setSteps(List.of(
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s0.getStepId()).assigneeId(staffUser.getUserId()).dependsOnStepIds(Collections.emptyList()).build(),
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s1.getStepId()).assigneeId(staffUser.getUserId()).dependsOnStepIds(List.of(s2.getStepId())).build(),
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s2.getStepId()).assigneeId(staffUser.getUserId()).dependsOnStepIds(List.of(s1.getStepId())).build()
        ));

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.STEP_CYCLE_DETECTED);
    }

    @Test
    @DisplayName("reviewTicket_whenNoAnchorStep_throwsAnchorStepRequired")
    void reviewTicket_whenNoAnchorStep_throwsAnchorStepRequired() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.INITIATED);

        SubCategory sc = SubCategory.builder().subCategoryId(UUID.randomUUID()).isActive(true).build();
        when(subCategoryRepository.findAllById(any())).thenReturn(List.of(sc));

        Step s1 = Step.builder().stepId(UUID.randomUUID()).build();
        Step s2 = Step.builder().stepId(UUID.randomUUID()).build();
        when(stepRepository.findAllById(any())).thenReturn(List.of(s1, s2));

        staffUser.setStatus(com.cityfix.backend.model.enums.UserStatus.ACTIVE);
        staffUser.setDepartment(new Department());
        when(userRepository.findAllById(any())).thenReturn(List.of(staffUser));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        ReviewTicketRequest request = new ReviewTicketRequest();
        request.setSubCategoryIds(List.of(sc.getSubCategoryId()));
        // both have dependencies -> no anchor
        request.setSteps(List.of(
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s1.getStepId()).assigneeId(staffUser.getUserId()).dependsOnStepIds(List.of(s2.getStepId())).build(),
                ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(s2.getStepId()).assigneeId(staffUser.getUserId()).dependsOnStepIds(List.of(s1.getStepId())).build()
        ));

        assertThatThrownBy(() -> managerTicketService.reviewTicket(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANCHOR_STEP_REQUIRED);
    }

    @Test
    @DisplayName("updateTicketStepDependencies_success")
    void updateTicketStepDependencies_success() {
        mockCurrentUser(managerUser);

        TicketStep ts1 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>()).build();
        TicketStep ts2 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>(Set.of(ts1))).build();
        TicketStep ts3 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>(Set.of(ts2))).build();

        when(ticketStepRepository.findById(ts3.getId())).thenReturn(Optional.of(ts3));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(ts1, ts2, ts3));
        when(ticketStepRepository.save(any(TicketStep.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(ts3.getId()).dependsOnStepIds(List.of(ts1.getId())).build()
        );

        UpdateTicketStepDependenciesRequest request = new UpdateTicketStepDependenciesRequest(List.of(ts1.getId()));
        com.cityfix.backend.model.dto.response.TicketStepResponse response =
                managerTicketService.updateTicketStepDependencies(ts3.getId(), request);

        assertThat(response).isNotNull();
        assertThat(ts3.getDependsOn()).containsExactly(ts1);
    }

    @Test
    @DisplayName("updateTicketStepDependencies_selfDependency_throwsInvalidStepDependency")
    void updateTicketStepDependencies_selfDependency_throwsInvalidStepDependency() {
        mockCurrentUser(managerUser);

        TicketStep ts1 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>()).build();

        when(ticketStepRepository.findById(ts1.getId())).thenReturn(Optional.of(ts1));

        UpdateTicketStepDependenciesRequest request = new UpdateTicketStepDependenciesRequest(List.of(ts1.getId()));

        assertThatThrownBy(() -> managerTicketService.updateTicketStepDependencies(ts1.getId(), request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STEP_DEPENDENCY);
    }

    @Test
    @DisplayName("updateTicketStepDependencies_cycleDetected_throwsStepCycleDetected")
    void updateTicketStepDependencies_cycleDetected_throwsStepCycleDetected() {
        mockCurrentUser(managerUser);

        TicketStep ts1 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>()).build();
        TicketStep ts2 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>(Set.of(ts1))).build();

        when(ticketStepRepository.findById(ts1.getId())).thenReturn(Optional.of(ts1));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(ts1, ts2));

        // Making ts1 depend on ts2 creates a cycle ts1 -> ts2 -> ts1 and leaves no anchor step!
        UpdateTicketStepDependenciesRequest request = new UpdateTicketStepDependenciesRequest(List.of(ts2.getId()));

        assertThatThrownBy(() -> managerTicketService.updateTicketStepDependencies(ts1.getId(), request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isIn(ErrorCode.STEP_CYCLE_DETECTED, ErrorCode.ANCHOR_STEP_REQUIRED);
    }

    @Test
    @DisplayName("updateTicketStepDependencies_crossTicket_throwsInvalidStepDependency")
    void updateTicketStepDependencies_crossTicket_throwsInvalidStepDependency() {
        mockCurrentUser(managerUser);

        TicketStep ts1 = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).dependsOn(new HashSet<>()).build();
        UUID outsideStepId = UUID.randomUUID();

        when(ticketStepRepository.findById(ts1.getId())).thenReturn(Optional.of(ts1));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(ts1));

        UpdateTicketStepDependenciesRequest request = new UpdateTicketStepDependenciesRequest(List.of(outsideStepId));

        assertThatThrownBy(() -> managerTicketService.updateTicketStepDependencies(ts1.getId(), request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STEP_DEPENDENCY);
    }
}
