package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.response.AdminAnalyticsResponse;
import com.cityfix.backend.model.dto.response.AdminDashboardSummaryResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.model.enums.UserStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.impl.AdminDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SubCategoryRepository subCategoryRepository;
    @Mock
    private StepRepository stepRepository;
    @Mock
    private RoutingRuleRepository routingRuleRepository;
    @Mock
    private RatingRepository ratingRepository;

    @InjectMocks
    private AdminDashboardServiceImpl adminDashboardService;

    private UUID deptId;
    private UUID catId;
    private Department department;
    private Category category;
    private SubCategory subCategory;
    private Ticket completedTicket;
    private Ticket overdueInProgressTicket;
    private Ticket initiatedUnassignedTicket;
    private TicketStep completedStep;
    private TicketStep activeStep;
    private Rating rating;

    @BeforeEach
    void setUp() {
        deptId = UUID.randomUUID();
        catId = UUID.randomUUID();

        department = Department.builder()
                .departmentId(deptId)
                .name("Sở Hạ tầng Kỹ thuật")
                .build();

        category = Category.builder()
                .categoryId(catId)
                .name("Chiếu sáng đô thị")
                .isActive(true)
                .build();

        subCategory = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .category(category)
                .name("Đèn đường hỏng")
                .priority(Priority.MEDIUM)
                .isActive(true)
                .build();

        User staffUser = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Cán bộ A")
                .department(department)
                .role(new Role(UUID.randomUUID(), "STAFF"))
                .status(UserStatus.ACTIVE)
                .build();

        LocalDateTime now = LocalDateTime.now();

        // 1. Completed ticket (resolved on time)
        completedTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Đèn đường hỏng số 1")
                .address("10 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .priority(Priority.MEDIUM)
                .status(TicketStatus.COMPLETED)
                .createdAt(now.minusHours(10))
                .updatedAt(now.minusHours(4))
                .deadline(now.minusHours(2))
                .build();
        completedStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(completedTicket)
                .assignee(staffUser)
                .status(AssignmentStatus.COMPLETED)
                .startedAt(now.minusHours(9))
                .completedAt(now.minusHours(4))
                .build();
        completedTicket.setSteps(List.of(completedStep));
        completedTicket.setSubCategories(List.of(TicketSubCategory.builder()
                .ticket(completedTicket)
                .subCategory(subCategory)
                .build()));

        // 2. Overdue IN_PROGRESS ticket
        overdueInProgressTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Đèn đường hỏng số 2")
                .address("20 Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh")
                .priority(Priority.HIGH)
                .status(TicketStatus.IN_PROGRESS)
                .isLifeThreatening(true)
                .createdAt(now.minusHours(30))
                .updatedAt(now.minusHours(5))
                .deadline(now.minusHours(3))
                .build();
        activeStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(overdueInProgressTicket)
                .assignee(staffUser)
                .status(AssignmentStatus.IN_PROGRESS)
                .startedAt(now.minusHours(28))
                .build();
        overdueInProgressTicket.setSteps(List.of(activeStep));
        overdueInProgressTicket.setSubCategories(List.of(TicketSubCategory.builder()
                .ticket(overdueInProgressTicket)
                .subCategory(subCategory)
                .build()));

        // 3. Initiated unassigned ticket
        initiatedUnassignedTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Rác thải tồn đọng")
                .address("50 Trần Hưng Đạo, Phường Cầu Kho, Quận 1, TP. Hồ Chí Minh")
                .priority(Priority.LOW)
                .status(TicketStatus.INITIATED)
                .needsManualReview(true)
                .createdAt(now.minusHours(2))
                .updatedAt(now.minusHours(2))
                .build();

        rating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(completedTicket)
                .rate(4)
                .feedback("Khá nhanh")
                .build();
    }

    @Test
    @DisplayName("getDashboardSummary_calculatesAllKpisUsingDbAggregationsAndBatchedDepartmentProcessing")
    @SuppressWarnings("unchecked")
    void getDashboardSummary_calculatesAllKpisCorrectly() {
        when(ticketRepository.countTicketsGroupByStatus()).thenReturn(List.of(
                new Object[]{TicketStatus.COMPLETED, 1L},
                new Object[]{TicketStatus.IN_PROGRESS, 1L},
                new Object[]{TicketStatus.INITIATED, 1L}
        ));
        when(ticketRepository.countUnassignedTickets()).thenReturn(1L);
        when(ticketRepository.countByNeedsManualReviewTrue()).thenReturn(1L);
        when(ticketRepository.countActiveLifeThreateningTickets(any())).thenReturn(1L);
        when(ticketRepository.countOverdueTickets(any(), any())).thenReturn(1L);
        when(ratingRepository.findAverageActiveRating()).thenReturn(4.0);

        when(userRepository.countUsersGroupByStatus()).thenReturn(List.of(
                new Object[]{UserStatus.ACTIVE, 3L},
                new Object[]{UserStatus.BLOCKED, 1L}
        ));
        when(userRepository.countUsersGroupByRole()).thenReturn(List.of(
                new Object[]{"RESIDENT", 1L},
                new Object[]{"STAFF", 1L},
                new Object[]{"MANAGER", 1L},
                new Object[]{"ADMIN", 1L}
        ));

        when(departmentRepository.findAllByIsDeletedFalse()).thenReturn(List.of(department));
        when(categoryRepository.countByIsDeletedFalse()).thenReturn(1L);
        when(categoryRepository.countByIsActiveTrueAndIsDeletedFalse()).thenReturn(1L);
        when(subCategoryRepository.countByIsDeletedFalse()).thenReturn(1L);
        when(subCategoryRepository.countByIsActiveTrueAndIsDeletedFalse()).thenReturn(1L);
        when(stepRepository.countByIsDeletedFalse()).thenReturn(1L);
        when(routingRuleRepository.findByIsDeletedFalse()).thenReturn(List.of(new RoutingRule()));

        // Simulate 2 paginated batches for department KPI calculation
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completedTicket, overdueInProgressTicket), PageRequest.of(0, 2), 3))
                .thenReturn(new PageImpl<>(List.of(initiatedUnassignedTicket), PageRequest.of(1, 2), 3));
        when(ticketStepRepository.findByTicketTicketIdIn(any()))
                .thenReturn(List.of(completedStep, activeStep))
                .thenReturn(List.of());

        AdminDashboardSummaryResponse summary = adminDashboardService.getDashboardSummary();

        assertThat(summary).isNotNull();
        // Ticket KPIs
        assertThat(summary.getTicketSummary().getTotalTickets()).isEqualTo(3L);
        assertThat(summary.getTicketSummary().getCompletedTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getInProgressTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getInitiatedTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getUnassignedTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getNeedsManualReviewTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getLifeThreateningTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getOverdueTickets()).isEqualTo(1L);
        assertThat(summary.getTicketSummary().getOverallResolutionRate()).isEqualTo(33.33);
        assertThat(summary.getTicketSummary().getAverageRating()).isEqualTo(4.0);

        // Account KPIs
        assertThat(summary.getAccountSummary().getTotalUsers()).isEqualTo(4L);
        assertThat(summary.getAccountSummary().getActiveUsers()).isEqualTo(3L);
        assertThat(summary.getAccountSummary().getBlockedUsers()).isEqualTo(1L);
        assertThat(summary.getAccountSummary().getResidentCount()).isEqualTo(1L);
        assertThat(summary.getAccountSummary().getStaffCount()).isEqualTo(1L);
        assertThat(summary.getAccountSummary().getManagerCount()).isEqualTo(1L);
        assertThat(summary.getAccountSummary().getAdminCount()).isEqualTo(1L);

        // Config KPIs
        assertThat(summary.getConfigSummary().getTotalDepartments()).isEqualTo(1L);
        assertThat(summary.getConfigSummary().getTotalCategories()).isEqualTo(1L);
        assertThat(summary.getConfigSummary().getTotalSubCategories()).isEqualTo(1L);
        assertThat(summary.getConfigSummary().getTotalSteps()).isEqualTo(1L);
        assertThat(summary.getConfigSummary().getTotalRoutingRules()).isEqualTo(1L);

        // Department KPIs across 2 batches
        assertThat(summary.getDepartmentSummaries()).hasSize(1);
        assertThat(summary.getDepartmentSummaries().get(0).getDepartmentId()).isEqualTo(deptId);
        assertThat(summary.getDepartmentSummaries().get(0).getTotalAssignedTickets()).isEqualTo(2L);
        assertThat(summary.getDepartmentSummaries().get(0).getResolvedOrCompletedTickets()).isEqualTo(1L);
        assertThat(summary.getDepartmentSummaries().get(0).getResolutionRate()).isEqualTo(50.0);

        verify(ticketRepository, never()).findAll(any(Specification.class));
        verify(ratingRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAnalytics_and_exportAnalyticsCsv_returnsCompleteAnalyticsAndUtf8Csv")
    @SuppressWarnings("unchecked")
    void getAnalytics_and_exportAnalyticsCsv_returnsCompleteAnalyticsAndUtf8Csv() {
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completedTicket, overdueInProgressTicket, initiatedUnassignedTicket)));
        when(ticketStepRepository.findByTicketTicketIdIn(any()))
                .thenReturn(List.of(completedStep, activeStep));
        when(ratingRepository.findByTicket_TicketIdInAndIsDeletedFalse(any())).thenReturn(List.of(rating));
        when(departmentRepository.findAllByIsDeletedFalse()).thenReturn(List.of(department));
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(category));
        when(routingRuleRepository.findByIsDeletedFalse()).thenReturn(List.of());

        AdminAnalyticsResponse analytics = adminDashboardService.getAnalytics(
                null, null, "MONTH", null, null, null, null
        );

        assertThat(analytics).isNotNull();
        assertThat(analytics.getOverview().getTotalTickets()).isEqualTo(3L);
        assertThat(analytics.getOverview().getResolvedOrCompletedTickets()).isEqualTo(1L);
        assertThat(analytics.getOverview().getOnTimeResolutionRate()).isEqualTo(100.0);
        assertThat(analytics.getOverview().getAverageResolutionHours()).isEqualTo(6.0);
        assertThat(analytics.getTimeSeries()).isNotEmpty();
        assertThat(analytics.getGeographicBreakdown()).hasSize(3);
        assertThat(analytics.getDepartmentBreakdown()).hasSize(1);
        assertThat(analytics.getCategoryBreakdown()).hasSize(1);

        byte[] csvBytes = adminDashboardService.exportAnalyticsCsv(
                null, null, "DAY", null, null, null, null
        );

        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvContent).startsWith("\uFEFF");
        assertThat(csvContent).contains("=== BAO CAO TONG QUAN HE THONG (OVERVIEW) ===");
        assertThat(csvContent).contains("=== XU HUONG SU CO THEO THOI GIAN (TIME SERIES) ===");
        assertThat(csvContent).contains("=== THONG KE THEO KHU VUC DIA LY (GEOGRAPHIC BREAKDOWN) ===");
        assertThat(csvContent).contains("Sở Hạ tầng Kỹ thuật");
        assertThat(csvContent).contains("Chiếu sáng đô thị");
    }

    @Test
    @DisplayName("getAnalytics_unassignedTicketMatchedByRoutingRule_includedInDepartmentBreakdown")
    @SuppressWarnings("unchecked")
    void getAnalytics_unassignedTicketMatchedByRoutingRule_includedInDepartmentBreakdown() {
        initiatedUnassignedTicket.setAddress("12 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh");
        initiatedUnassignedTicket.setSubCategories(List.of(TicketSubCategory.builder()
                .ticket(initiatedUnassignedTicket)
                .subCategory(subCategory)
                .build()));

        RoutingRule routingRule = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(department)
                .province("TP. Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();

        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(completedTicket, initiatedUnassignedTicket)));
        when(ticketStepRepository.findByTicketTicketIdIn(any()))
                .thenReturn(List.of(completedStep));
        when(ratingRepository.findByTicket_TicketIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(departmentRepository.findAllByIsDeletedFalse()).thenReturn(List.of(department));
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(category));
        when(routingRuleRepository.findByIsDeletedFalse()).thenReturn(List.of(routingRule));

        AdminAnalyticsResponse analytics = adminDashboardService.getAnalytics(
                null, null, "DAY", null, null, deptId, null
        );

        assertThat(analytics.getDepartmentBreakdown()).hasSize(1);
        AdminAnalyticsResponse.DepartmentAnalyticsItem deptItem = analytics.getDepartmentBreakdown().get(0);
        // Includes both completedTicket (assigned via step) and initiatedUnassignedTicket (matched via RoutingRule)
        assertThat(deptItem.getTotalTickets()).isEqualTo(2L);
        assertThat(deptItem.getResolvedOrCompletedTickets()).isEqualTo(1L);
        assertThat(deptItem.getInProgressTickets()).isEqualTo(1L);
        assertThat(deptItem.getResolutionRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("getAnalytics_resolvedTicketsMissingSlaData_excludedFromOnTimeRateDenominator")
    @SuppressWarnings("unchecked")
    void getAnalytics_resolvedTicketsMissingSlaData_excludedFromOnTimeRateDenominator() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Late resolved ticket (completedAt AFTER deadline -> NOT on time)
        Ticket lateResolvedTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố trễ hạn")
                .priority(Priority.HIGH)
                .status(TicketStatus.RESOLVED)
                .createdAt(now.minusHours(20))
                .updatedAt(now.minusHours(1))
                .deadline(now.minusHours(5))
                .build();
        TicketStep lateStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(lateResolvedTicket)
                .assignee(completedStep.getAssignee())
                .status(AssignmentStatus.RESOLVED)
                .completedAt(now.minusHours(1))
                .build();
        lateResolvedTicket.setSteps(List.of(lateStep));

        // 2. Resolved ticket without deadline (missing SLA data -> must NOT inflate onTimeResolutionRate)
        Ticket noDeadlineResolvedTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố không có deadline")
                .priority(Priority.LOW)
                .status(TicketStatus.COMPLETED)
                .createdAt(now.minusHours(10))
                .updatedAt(now.minusHours(2))
                .deadline(null)
                .build();
        TicketStep noDeadlineStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(noDeadlineResolvedTicket)
                .assignee(completedStep.getAssignee())
                .status(AssignmentStatus.COMPLETED)
                .completedAt(now.minusHours(2))
                .build();
        noDeadlineResolvedTicket.setSteps(List.of(noDeadlineStep));

        // 3. Resolved ticket with deadline and updatedAt before deadline, but missing TicketStep.completedAt
        // Must NOT fallback to updatedAt and must be excluded from SLA denominator
        Ticket noStepCompletedAtTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố thiếu completedAt trên step")
                .priority(Priority.MEDIUM)
                .status(TicketStatus.COMPLETED)
                .createdAt(now.minusHours(12))
                .updatedAt(now.minusHours(8))
                .deadline(now.minusHours(2))
                .build();
        TicketStep stepWithoutCompletedAt = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(noStepCompletedAtTicket)
                .assignee(completedStep.getAssignee())
                .status(AssignmentStatus.COMPLETED)
                .completedAt(null)
                .build();
        noStepCompletedAtTicket.setSteps(List.of(stepWithoutCompletedAt));

        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lateResolvedTicket, noDeadlineResolvedTicket, noStepCompletedAtTicket)));
        when(ticketStepRepository.findByTicketTicketIdIn(any()))
                .thenReturn(List.of(lateStep, noDeadlineStep, stepWithoutCompletedAt));
        when(ratingRepository.findByTicket_TicketIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(departmentRepository.findAllByIsDeletedFalse()).thenReturn(List.of(department));
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of());
        when(routingRuleRepository.findByIsDeletedFalse()).thenReturn(List.of());

        AdminAnalyticsResponse analytics = adminDashboardService.getAnalytics(
                null, null, "DAY", null, null, null, null
        );

        // Total resolved = 3, but only lateResolvedTicket has both deadline and TicketStep.completedAt, and it was late -> 0.0% on-time rate
        assertThat(analytics.getOverview().getResolvedOrCompletedTickets()).isEqualTo(3L);
        assertThat(analytics.getOverview().getOnTimeResolutionRate()).isEqualTo(0.0);
        assertThat(analytics.getDepartmentBreakdown()).hasSize(1);
        assertThat(analytics.getDepartmentBreakdown().get(0).getOnTimeRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("exportAnalyticsCsv_formulaPrefixInValues_neutralizedWithSingleQuote")
    @SuppressWarnings("unchecked")
    void exportAnalyticsCsv_formulaPrefixInValues_neutralizedWithSingleQuote() {
        Ticket formulaTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Test CSV injection")
                .address("12 Đường A, =CMD|' /C calc'!A0, Quận 1, @SUM(1+1)")
                .priority(Priority.LOW)
                .status(TicketStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .build();

        Department formulaDept = Department.builder()
                .departmentId(UUID.randomUUID())
                .name("+Sở Công thức")
                .build();

        Category formulaCat = Category.builder()
                .categoryId(UUID.randomUUID())
                .name("-Danh mục âm")
                .isActive(true)
                .build();

        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(formulaTicket)));
        when(ticketStepRepository.findByTicketTicketIdIn(any())).thenReturn(List.of());
        when(ratingRepository.findByTicket_TicketIdInAndIsDeletedFalse(any())).thenReturn(List.of());
        when(departmentRepository.findAllByIsDeletedFalse()).thenReturn(List.of(formulaDept));
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(formulaCat));
        when(routingRuleRepository.findByIsDeletedFalse()).thenReturn(List.of());

        byte[] csvBytes = adminDashboardService.exportAnalyticsCsv(
                null, null, "DAY", null, null, null, null
        );
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).contains("'@SUM(1+1)");
        assertThat(csv).contains("'=CMD|' /C calc'!A0");
        assertThat(csv).contains("'+Sở Công thức");
        assertThat(csv).contains("'-Danh mục âm");
    }
}
