package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.NotificationMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.UpdateTicketDurationRequest;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock
    private EmailService emailService;
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
    @DisplayName("updateTicketDuration_byManager_singleSubCategory_success")
    void updateTicketDuration_byManager_singleSubCategory_success() {
        mockCurrentUser(managerUser);

        TicketSubCategory tsc = new TicketSubCategory();
        ticket.setSubCategories(List.of(tsc));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).duration(72).build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        UpdateTicketDurationRequest request = new UpdateTicketDurationRequest(72);
        TicketResponse response = managerTicketService.updateTicketDuration(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(response.getDuration()).isEqualTo(72);
        assertThat(ticket.getDuration()).isEqualTo(72);
    }

    @Test
    @DisplayName("updateTicketDuration_byManager_multiSubCategories_throwsInvalidRequest")
    void updateTicketDuration_byManager_multiSubCategories_throwsInvalidRequest() {
        mockCurrentUser(managerUser);

        TicketSubCategory tsc1 = new TicketSubCategory();
        TicketSubCategory tsc2 = new TicketSubCategory();
        ticket.setSubCategories(List.of(tsc1, tsc2));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        UpdateTicketDurationRequest request = new UpdateTicketDurationRequest(72);

        assertThatThrownBy(() -> managerTicketService.updateTicketDuration(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("updateTicketDuration_byNonManager_throwsForbidden")
    void updateTicketDuration_byNonManager_throwsForbidden() {
        mockCurrentUser(staffUser);

        UpdateTicketDurationRequest request = new UpdateTicketDurationRequest(72);

        assertThatThrownBy(() -> managerTicketService.updateTicketDuration(ticketId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("rejectInitiatedTicket_cascadesCancelSteps_andSetsTimestamps")
    void rejectInitiatedTicket_cascadesCancelSteps_andSetsTimestamps() {
        mockCurrentUser(managerUser);

        ticket.setStatus(com.cityfix.backend.model.enums.TicketStatus.INITIATED);

        TicketStep activeStep = new TicketStep();
        activeStep.setId(UUID.randomUUID());
        activeStep.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.OPEN);

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
        when(ticketService.calculateTicketDuration(any(Ticket.class))).thenReturn(24);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketHistory mockHistory = TicketHistory.builder()
                .actionType(com.cityfix.backend.model.enums.ActionType.ROUTED)
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
                        .orderIndex(0)
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
        assertThat(ticket.getSteps().get(0).getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.OPEN);
        assertThat(ticket.getSteps().get(0).getOrderIndex()).isEqualTo(0);

        verify(notificationRepository).save(any());
        verify(ticketHistoryRepository).save(argThat(h ->
                h != null
                        && h.getActionType() == com.cityfix.backend.model.enums.ActionType.ROUTED
                        && h.getCurrentStatus() == TicketStatus.IN_PROGRESS
        ));
    }

    @Test
    @DisplayName("reviewTicket_ticketNotInitiated_throwsTICKET_NOT_INITIATED")
    void reviewTicket_ticketNotInitiated_throwsTICKET_NOT_INITIATED() {
        mockCurrentUser(managerUser);
        ticket.setStatus(TicketStatus.OPEN); // Not INITIATED

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
                                .stepId(stepId).assigneeId(UUID.randomUUID()).orderIndex(0).build(),
                        com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                                .stepId(stepId).assigneeId(UUID.randomUUID()).orderIndex(1).build()
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
                                .orderIndex(0)
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
        assertThat(result.get(0).getStaffs().get(0).getUsername()).isEqualTo("Trần Cán Bộ");
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
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse mockResponse = TicketResponse.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build();
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketHistory mockHistory = TicketHistory.builder()
                .actionType(com.cityfix.backend.model.enums.ActionType.ROUTED)
                .currentStatus(TicketStatus.IN_PROGRESS)
                .build();
        when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any())).thenReturn(mockHistory);

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = com.cityfix.backend.model.dto.request.ReviewTicketRequest.builder()
                .subCategoryIds(List.of(subCatId))
                .steps(List.of(com.cityfix.backend.model.dto.request.ReviewTicketRequest.StepAssignmentRequest.builder()
                        .stepId(stepId)
                        .assigneeId(staffInDept.getUserId())
                        .orderIndex(0)
                        .deadline(null) // Không truyền deadline
                        .build()))
                .build();

        LocalDateTime before = LocalDateTime.now();
        TicketResponse response = managerTicketService.reviewTicket(ticketId, request);

        assertThat(response).isNotNull();
        assertThat(ticket.getSteps()).hasSize(1);
        assertThat(ticket.getSteps().get(0).getDeadline()).isNotNull();
        assertThat(ticket.getSteps().get(0).getDeadline()).isAfterOrEqualTo(before.plusHours(6).minusSeconds(5));
    }
}
