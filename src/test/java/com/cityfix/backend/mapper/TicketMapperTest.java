package com.cityfix.backend.mapper;

import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.Role;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.TicketSubCategory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.AssignmentStatus;
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
        Department department = Department.builder()
                .departmentId(UUID.randomUUID())
                .name("Phòng Quản lý Đô thị")
                .build();

        User staff = User.builder()
                .userId(UUID.randomUUID())
                .department(department)
                .build();

        TicketStep step = TicketStep.builder()
                .id(UUID.randomUUID())
                .assignee(staff)
                .status(AssignmentStatus.IN_PROGRESS)
                .isDeleted(false)
                .orderIndex(1)
                .build();

        ticket.setSteps(List.of(step));

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getDepartmentId()).isEqualTo(department.getDepartmentId());
        assertThat(response.getDepartmentName()).isEqualTo("Phòng Quản lý Đô thị");
        assertThat(response.getAssigneeId()).isEqualTo(staff.getUserId());
    }

    @Test
    @DisplayName("toTicketResponse_noSteps_departmentIsNullInPureMapper")
    void toTicketResponse_noSteps_departmentIsNullInPureMapper() {
        ticket.setSteps(Collections.emptyList());

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getDepartmentId()).isNull();
        assertThat(response.getDepartmentName()).isNull();
        assertThat(response.getAssigneeId()).isNull();
        assertThat(response.getAssigneeName()).isNull();
    }

    @Test
    @DisplayName("toTicketResponse_withMultipleSubCategories_mapsCategoriesList")
    void toTicketResponse_withMultipleSubCategories_mapsCategoriesList() {
        Category cat1 = Category.builder().categoryId(UUID.randomUUID()).name("Hạ tầng").build();
        Category cat2 = Category.builder().categoryId(UUID.randomUUID()).name("Môi trường").build();

        SubCategory sub1 = SubCategory.builder().subCategoryId(UUID.randomUUID()).category(cat1).name("Ổ gà").build();
        SubCategory sub2 = SubCategory.builder().subCategoryId(UUID.randomUUID()).category(cat1).name("Cống nghẹt").build();
        SubCategory sub3 = SubCategory.builder().subCategoryId(UUID.randomUUID()).category(cat2).name("Rác thải").build();

        TicketSubCategory tsc1 = TicketSubCategory.builder().subCategory(sub1).build();
        TicketSubCategory tsc2 = TicketSubCategory.builder().subCategory(sub2).build();
        TicketSubCategory tsc3 = TicketSubCategory.builder().subCategory(sub3).build();

        ticket.setSubCategories(List.of(tsc1, tsc2, tsc3));

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getCategories()).hasSize(2);
        assertThat(response.getCategories().get(0).getCategoryId()).isEqualTo(cat1.getCategoryId());
        assertThat(response.getCategories().get(0).getCategoryName()).isEqualTo("Hạ tầng");
        assertThat(response.getCategories().get(1).getCategoryId()).isEqualTo(cat2.getCategoryId());
        assertThat(response.getCategories().get(1).getCategoryName()).isEqualTo("Môi trường");
        assertThat(response.getCategoryId()).isEqualTo(cat1.getCategoryId());
        assertThat(response.getCategoryName()).isEqualTo("Hạ tầng");
    }

    @Test
    @DisplayName("toTicketResponse_mapsDeadline")
    void toTicketResponse_mapsDeadline() {
        java.time.LocalDateTime deadline = java.time.LocalDateTime.of(2026, 10, 20, 15, 30);
        ticket.setDeadline(deadline);

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getDeadline()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("toTicketResponse_mapsAndSortsStepsByOrderIndex")
    void toTicketResponse_mapsAndSortsStepsByOrderIndex() {
        TicketStep step2 = TicketStep.builder().id(UUID.randomUUID()).orderIndex(2).build();
        TicketStep step1 = TicketStep.builder().id(UUID.randomUUID()).orderIndex(1).build();
        ticket.setSteps(List.of(step2, step1));

        com.cityfix.backend.model.dto.response.TicketStepResponse res2 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(step2.getId()).orderIndex(2).build();
        com.cityfix.backend.model.dto.response.TicketStepResponse res1 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(step1.getId()).orderIndex(1).build();

        when(ticketStepMapper.toTicketStepResponseList(ticket.getSteps())).thenReturn(List.of(res2, res1));

        TicketResponse response = ticketMapper.toTicketResponse(ticket);

        assertThat(response).isNotNull();
        assertThat(response.getSteps()).hasSize(2);
        assertThat(response.getSteps().get(0).getOrderIndex()).isEqualTo(1);
        assertThat(response.getSteps().get(1).getOrderIndex()).isEqualTo(2);
    }
}
