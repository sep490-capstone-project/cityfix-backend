package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketMapperTest {

    @Mock
    private TicketStepMapper ticketStepMapper;

    @Mock
    private SubCategoryMapper subCategoryMapper;

    @Mock
    private com.cityfix.backend.repository.RoutingRuleRepository routingRuleRepository;

    @InjectMocks
    private TicketMapperImpl ticketMapper;

    private User reporter;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        reporter = new User();
        reporter.setUserId(UUID.randomUUID());
        reporter.setEmail("citizen@cityfix.com");
        reporter.setFullName("Nguyen Van A");
        reporter.setPhoneNumber("0987654321");

        ticket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .user(reporter)
                .isAnonymous(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("toTicketResponse_anonymousTicket_adminUser_preservesCreatedBy")
    void toTicketResponse_anonymousTicket_adminUser_preservesCreatedBy() {
        User adminUser = new User();
        adminUser.setUserId(UUID.randomUUID());
        adminUser.setRole(new Role(UUID.randomUUID(), "ADMIN"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                adminUser, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getCreatedBy()).isEqualTo(reporter.getUserId());
        assertThat(response.getCreatorFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getCreatorPhoneNumber()).isEqualTo("0987654321");
        assertThat(response.getIsAnonymous()).isTrue();
    }

    @Test
    @DisplayName("toTicketResponse_anonymousTicket_residentUser_masksCreatedBy")
    void toTicketResponse_anonymousTicket_residentUser_masksCreatedBy() {
        User residentUser = new User();
        residentUser.setUserId(UUID.randomUUID());
        residentUser.setRole(new Role(UUID.randomUUID(), "RESIDENT"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                residentUser, null, List.of(new SimpleGrantedAuthority("ROLE_RESIDENT")));
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getCreatedBy()).isNull();
        assertThat(response.getCreatorFullName()).isNull();
        assertThat(response.getCreatorPhoneNumber()).isNull();
        assertThat(response.getIsAnonymous()).isTrue();
    }

    @Test
    @DisplayName("toTicketResponse_anonymousTicket_unauthenticated_masksCreatedBy")
    void toTicketResponse_anonymousTicket_unauthenticated_masksCreatedBy() {
        SecurityContextHolder.clearContext();

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getCreatedBy()).isNull();
        assertThat(response.getCreatorFullName()).isNull();
        assertThat(response.getCreatorPhoneNumber()).isNull();
        assertThat(response.getIsAnonymous()).isTrue();
    }

    @Test
    @DisplayName("toTicketResponse_anonymousTicket_anonymousAuthenticationToken_masksCreatedBy")
    void toTicketResponse_anonymousTicket_anonymousAuthenticationToken_masksCreatedBy() {
        AnonymousAuthenticationToken anonAuth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(anonAuth);
        SecurityContextHolder.setContext(securityContext);

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getCreatedBy()).isNull();
        assertThat(response.getCreatorFullName()).isNull();
        assertThat(response.getCreatorPhoneNumber()).isNull();
        assertThat(response.getIsAnonymous()).isTrue();
    }

    @Test
    @DisplayName("toTicketResponse_nonAnonymousTicket_residentUser_preservesCreatedBy")
    void toTicketResponse_nonAnonymousTicket_residentUser_preservesCreatedBy() {
        ticket.setIsAnonymous(false);

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getCreatedBy()).isEqualTo(reporter.getUserId());
        assertThat(response.getCreatorFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getCreatorPhoneNumber()).isEqualTo("0987654321");
        assertThat(response.getIsAnonymous()).isFalse();
    }

    @Test
    @DisplayName("toTicketResponse_withActiveStep_mapsDepartmentFromStep")
    void toTicketResponse_withActiveStep_mapsDepartmentFromStep() {
        com.cityfix.backend.model.entity.Department department = com.cityfix.backend.model.entity.Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Phòng Quản lý Đô thị")
                .build();

        User staff = User.builder()
                .userId(UUID.randomUUID())
                .department(department)
                .build();

        com.cityfix.backend.model.entity.TicketStep step = com.cityfix.backend.model.entity.TicketStep.builder()
                .id(UUID.randomUUID())
                .assignee(staff)
                .status(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS)
                .isDeleted(false)
                .orderIndex(1)
                .build();

        ticket.setSteps(List.of(step));

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getDepartmentId()).isEqualTo(department.getDepartmentId());
        assertThat(response.getDepartmentName()).isEqualTo("Phòng Quản lý Đô thị");
    }

    @Test
    @DisplayName("toTicketResponse_noSteps_mapsDepartmentFromRoutingRule")
    void toTicketResponse_noSteps_mapsDepartmentFromRoutingRule() {
        UUID categoryId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Category category = com.cityfix.backend.model.entity.Category.builder()
                .categoryId(categoryId)
                .name("Giao thông")
                .build();

        ticket.setCategory(category);
        ticket.setAddress("Số 10 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP Hồ Chí Minh");
        ticket.setSteps(Collections.emptyList());

        com.cityfix.backend.model.entity.Department department = com.cityfix.backend.model.entity.Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Sở Giao thông Vận tải")
                .build();

        com.cityfix.backend.model.entity.RoutingRule rule = com.cityfix.backend.model.entity.RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(department)
                .province("TP Hồ Chí Minh")
                .ward("Bến Nghé")
                .build();

        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(categoryId))
                .thenReturn(List.of(rule));

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getDepartmentId()).isEqualTo(department.getDepartmentId());
        assertThat(response.getDepartmentName()).isEqualTo("Sở Giao thông Vận tải");
    }

    @Test
    @DisplayName("toTicketResponse_noStepsAndNoRule_departmentIsNull")
    void toTicketResponse_noStepsAndNoRule_departmentIsNull() {
        UUID categoryId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Category category = com.cityfix.backend.model.entity.Category.builder()
                .categoryId(categoryId)
                .name("Môi trường")
                .build();

        ticket.setCategory(category);
        ticket.setSteps(Collections.emptyList());

        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(categoryId))
                .thenReturn(Collections.emptyList());

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getDepartmentId()).isNull();
        assertThat(response.getDepartmentName()).isNull();
    }
}
