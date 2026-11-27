package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Department;
import com.cityfix.backend.model.entity.RoutingRule;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.TicketStep;
import com.cityfix.backend.model.entity.TicketSubCategory;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.RoutingRuleRepository;
import com.cityfix.backend.repository.TicketStepRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.service.impl.ManagerDepartmentAccessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerDepartmentAccessValidatorTest {

    @Mock
    private UserDepartmentRepository userDepartmentRepository;

    @Mock
    private TicketStepRepository ticketStepRepository;

    @Mock
    private RoutingRuleRepository routingRuleRepository;

    @InjectMocks
    private ManagerDepartmentAccessValidator validator;

    private Department deptA;
    private Department deptB;
    private Department deptC;
    private User managerA;
    private User managerC;
    private User managerNoDept;
    private User staffA;
    private User staffB;

    @BeforeEach
    void setUp() {
        deptA = Department.builder().departmentId(UUID.randomUUID()).name("Phòng A").build();
        deptB = Department.builder().departmentId(UUID.randomUUID()).name("Phòng B").build();
        deptC = Department.builder().departmentId(UUID.randomUUID()).name("Phòng C").build();

        managerA = User.builder().userId(UUID.randomUUID()).email("mgrA@cityfix.vn").department(deptA).build();
        managerC = User.builder().userId(UUID.randomUUID()).email("mgrC@cityfix.vn").department(deptC).build();
        managerNoDept = User.builder().userId(UUID.randomUUID()).email("mgrNone@cityfix.vn").build();

        staffA = User.builder().userId(UUID.randomUUID()).email("staffA@cityfix.vn").department(deptA).build();
        staffB = User.builder().userId(UUID.randomUUID()).email("staffB@cityfix.vn").department(deptB).build();
    }

    @Test
    @DisplayName("validateTicketAccess_managerWithoutDepartment_throwsUserNotInDepartment")
    void validateTicketAccess_managerWithoutDepartment_throwsUserNotInDepartment() {
        when(userDepartmentRepository.findDepartmentsByUserId(managerNoDept.getUserId()))
                .thenReturn(Collections.emptyList());

        Ticket ticket = Ticket.builder().ticketId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> validator.validateTicketAccess(managerNoDept, ticket))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_IN_DEPARTMENT);
    }

    @Test
    @DisplayName("validateTicketAccess_ticketHasStepInManagerDept_succeeds")
    void validateTicketAccess_ticketHasStepInManagerDept_succeeds() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().ticketId(ticketId).status(TicketStatus.IN_PROGRESS).build();
        TicketStep stepA = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).assignee(staffA).status(AssignmentStatus.IN_PROGRESS).build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(stepA));
        when(userDepartmentRepository.findDepartmentsByUserId(staffA.getUserId())).thenReturn(List.of(deptA));

        assertThatCode(() -> validator.validateTicketAccess(managerA, ticket)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTicketAccess_ticketStepsOnlyInOtherDept_throwsForbidden")
    void validateTicketAccess_ticketStepsOnlyInOtherDept_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().ticketId(ticketId).status(TicketStatus.IN_PROGRESS).build();
        TicketStep stepB = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).assignee(staffB).status(AssignmentStatus.IN_PROGRESS).build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(stepB));
        when(userDepartmentRepository.findDepartmentsByUserId(staffB.getUserId())).thenReturn(List.of(deptB));

        assertThatThrownBy(() -> validator.validateTicketAccess(managerA, ticket))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateStepAccess_multiDeptTicket_strictAssigneeMode_blocksOtherDeptStep")
    void validateStepAccess_multiDeptTicket_strictAssigneeMode_blocksOtherDeptStep() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().ticketId(ticketId).status(TicketStatus.RESOLVED).build();
        TicketStep stepA = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).assignee(staffA).status(AssignmentStatus.RESOLVED).build();
        TicketStep stepB = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).assignee(staffB).status(AssignmentStatus.RESOLVED).build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(userDepartmentRepository.findDepartmentsByUserId(staffA.getUserId())).thenReturn(List.of(deptA));
        when(userDepartmentRepository.findDepartmentsByUserId(staffB.getUserId())).thenReturn(List.of(deptB));

        // Manager A can approve Dept A's step
        assertThatCode(() -> validator.validateStepAccess(managerA, stepA, true)).doesNotThrowAnyException();

        // Manager A CANNOT approve Dept B's step when strict mode is true (Option 2A for steps/{stepId}/status)
        assertThatThrownBy(() -> validator.validateStepAccess(managerA, stepB, true))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateStepAccess_multiDeptTicket_ticketLevelMode_allowsParticipatingDeptAndBlocksUnrelatedManager")
    void validateStepAccess_multiDeptTicket_ticketLevelMode_allowsParticipatingDeptAndBlocksUnrelatedManager() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().ticketId(ticketId).status(TicketStatus.IN_PROGRESS).build();
        TicketStep stepA = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).assignee(staffA).status(AssignmentStatus.IN_PROGRESS).build();
        TicketStep stepB = TicketStep.builder().id(UUID.randomUUID()).ticket(ticket).assignee(staffB).status(AssignmentStatus.ASSIGNED).build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(userDepartmentRepository.findDepartmentsByUserId(managerC.getUserId())).thenReturn(List.of(deptC));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(List.of(stepA, stepB));
        when(userDepartmentRepository.findDepartmentsByUserId(staffA.getUserId())).thenReturn(List.of(deptA));
        when(userDepartmentRepository.findDepartmentsByUserId(staffB.getUserId())).thenReturn(List.of(deptB));

        // Manager A participates in ticket -> allowed for ticket-level step operations (deadline/dependencies/delete)
        assertThatCode(() -> validator.validateStepAccess(managerA, stepB, false)).doesNotThrowAnyException();

        // Manager C has no relation to ticket -> FORBIDDEN
        assertThatThrownBy(() -> validator.validateStepAccess(managerC, stepB, false))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateTicketAccess_initiatedTicketMatchingRoutingRuleWithPrefix_succeeds")
    void validateTicketAccess_initiatedTicketMatchingRoutingRuleWithPrefix_succeeds() {
        UUID ticketId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category cat = Category.builder().categoryId(catId).name("Giao thông").build();
        SubCategory subCat = SubCategory.builder().subCategoryId(UUID.randomUUID()).category(cat).name("Ổ gà").build();

        Ticket ticket = Ticket.builder()
                .ticketId(ticketId)
                .status(TicketStatus.INITIATED)
                .address("123 Lê Lợi, Bến Nghé, Quận 1, Hồ Chí Minh")
                .subCategories(List.of(TicketSubCategory.builder().subCategory(subCat).build()))
                .build();

        RoutingRule rule = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(cat)
                .department(deptA)
                .province("TP. Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(Collections.emptyList());
        when(routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(Set.of(catId)))
                .thenReturn(List.of(rule));

        assertThatCode(() -> validator.validateTicketAccess(managerA, ticket)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateTicketAccess_initiatedTicketMatchingOtherDeptRule_throwsForbidden")
    void validateTicketAccess_initiatedTicketMatchingOtherDeptRule_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category cat = Category.builder().categoryId(catId).name("Giao thông").build();
        SubCategory subCat = SubCategory.builder().subCategoryId(UUID.randomUUID()).category(cat).name("Ổ gà").build();

        Ticket ticket = Ticket.builder()
                .ticketId(ticketId)
                .status(TicketStatus.INITIATED)
                .address("123 Lê Lợi, Phường Bến Nghé, TP. Hồ Chí Minh")
                .subCategories(List.of(TicketSubCategory.builder().subCategory(subCat).build()))
                .build();

        RoutingRule ruleForDeptB = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(cat)
                .department(deptB)
                .province("TP. Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(Collections.emptyList());
        when(routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(Set.of(catId)))
                .thenReturn(List.of(ruleForDeptB));

        assertThatThrownBy(() -> validator.validateTicketAccess(managerA, ticket))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateTicketAccess_initiatedTicketNoMatchingRoutingRule_throwsForbidden")
    void validateTicketAccess_initiatedTicketNoMatchingRoutingRule_throwsForbidden() {
        UUID ticketId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category cat = Category.builder().categoryId(catId).name("Giao thông").build();
        SubCategory subCat = SubCategory.builder().subCategoryId(UUID.randomUUID()).category(cat).name("Ổ gà").build();

        Ticket ticket = Ticket.builder()
                .ticketId(ticketId)
                .status(TicketStatus.INITIATED)
                .address("10 Tràng Tiền, Hoàn Kiếm, Hà Nội")
                .subCategories(List.of(TicketSubCategory.builder().subCategory(subCat).build()))
                .build();

        RoutingRule ruleHcm = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(cat)
                .department(deptA)
                .province("TP. Hồ Chí Minh")
                .build();

        when(userDepartmentRepository.findDepartmentsByUserId(managerA.getUserId())).thenReturn(List.of(deptA));
        when(ticketStepRepository.findByTicketTicketId(ticketId)).thenReturn(Collections.emptyList());
        when(routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(Set.of(catId)))
                .thenReturn(List.of(ruleHcm));

        assertThatThrownBy(() -> validator.validateTicketAccess(managerA, ticket))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
