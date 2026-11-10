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
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private TicketRepository ticketRepository;
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
        TicketStep completedStep = TicketStep.builder()
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
        TicketStep activeStep = TicketStep.builder()
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
    @DisplayName("getDashboardSummary_calculatesAllKpisCorrectly")
    @SuppressWarnings("unchecked")
    void getDashboardSummary_calculatesAllKpisCorrectly() {
        User resident = User.builder()
                .userId(UUID.randomUUID())
                .role(new Role(UUID.randomUUID(), "RESIDENT"))
                .status(UserStatus.ACTIVE)
                .build();
        User staff = User.builder()
                .userId(UUID.randomUUID())
                .role(new Role(UUID.randomUUID(), "STAFF"))
                .status(UserStatus.ACTIVE)
                .build();
        User manager = User.builder()
                .userId(UUID.randomUUID())
                .role(new Role(UUID.randomUUID(), "MANAGER"))
                .status(UserStatus.BLOCKED)
                .build();
        User admin = User.builder()
                .userId(UUID.randomUUID())
                .role(new Role(UUID.randomUUID(), "ADMIN"))
                .status(UserStatus.ACTIVE)
                .build();

        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(completedTicket, overdueInProgressTicket, initiatedUnassignedTicket));
        when(ratingRepository.findAll()).thenReturn(List.of(rating));
        when(userRepository.findAllByIsDeletedFalse()).thenReturn(List.of(resident, staff, manager, admin));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(category));
        when(subCategoryRepository.findByIsDeletedFalse()).thenReturn(List.of(subCategory));
        when(stepRepository.findByIsDeletedFalse()).thenReturn(List.of(new Step()));
        when(routingRuleRepository.findAll()).thenReturn(List.of(new RoutingRule()));

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

        // Department KPIs
        assertThat(summary.getDepartmentSummaries()).hasSize(1);
        assertThat(summary.getDepartmentSummaries().get(0).getDepartmentId()).isEqualTo(deptId);
        assertThat(summary.getDepartmentSummaries().get(0).getTotalAssignedTickets()).isEqualTo(2L);
        assertThat(summary.getDepartmentSummaries().get(0).getResolvedOrCompletedTickets()).isEqualTo(1L);
        assertThat(summary.getDepartmentSummaries().get(0).getResolutionRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("getAnalytics_and_exportAnalyticsCsv_returnsCompleteAnalyticsAndUtf8Csv")
    @SuppressWarnings("unchecked")
    void getAnalytics_and_exportAnalyticsCsv_returnsCompleteAnalyticsAndUtf8Csv() {
        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(completedTicket, overdueInProgressTicket, initiatedUnassignedTicket));
        when(ratingRepository.findAll()).thenReturn(List.of(rating));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(categoryRepository.findAllByIsDeletedFalse()).thenReturn(List.of(category));

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
}
