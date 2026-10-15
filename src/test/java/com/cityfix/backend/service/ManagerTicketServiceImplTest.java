package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.UpdateTicketDurationRequest;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.TicketHistoryRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.repository.UserRepository;
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
        mockCurrentUser(managerUser);

        UUID deptId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        Page<Ticket> ticketPage = new PageImpl<>(List.of(t));

        when(ticketRepository.findTicketsByDepartmentAndCriteria(deptId, SeverityLevel.HIGH, Priority.HIGH, TicketStatus.IN_PROGRESS, pageable))
                .thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(t)).thenReturn(TicketResponse.builder().id(t.getTicketId()).build());

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
}
