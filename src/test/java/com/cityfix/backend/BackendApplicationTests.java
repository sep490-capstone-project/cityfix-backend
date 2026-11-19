package com.cityfix.backend;

import com.cityfix.backend.config.FirebaseConfig;
import com.cityfix.backend.config.JwtAuthenticationFilter;
import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.*;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.repository.TicketRepository;
import com.cityfix.backend.repository.UserDepartmentRepository;
import com.cityfix.backend.repository.UserRepository;
import com.cityfix.backend.service.AdminDashboardService;
import com.cityfix.backend.service.AdminTicketService;
import com.cityfix.backend.service.AdminUserService;
import com.cityfix.backend.service.DepartmentService;
import com.cityfix.backend.service.TokenService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "SECRET_KEY=dGhpc19pc19hX2RlZmF1bHRfc2VjcmV0X2tleV9mb3JfbG9jYWxfdGVzdGluZ190aGF0X3Nob3VsZF9iZV9sb25nX2Vub3VnaA==",
        "RESEND_API_KEY=test",
        "RESEND_FROM_EMAIL=test@test.com",
        "CLOUDINARY_CLOUD_NAME=test",
        "CLOUDINARY_API_KEY=test",
        "CLOUDINARY_API_SECRET=test",
        "GEMINI_API_KEY=test"
})
@Disabled("Requires live PostgreSQL instance")
class BackendApplicationTests {

    @MockitoBean
    private FirebaseConfig firebaseConfig;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private SubCategoryRepository subCategoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private AdminTicketService adminTicketService;

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private DepartmentService departmentService;

    @Test
    void contextLoadsAndVerifiesDatabaseRefactoringAndRealHttpApis() throws Exception {
        // 1. Verify Flyway migrations V36, V37, V38 succeeded in flyway_schema_history
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('36', '37', '38') AND success = true",
                Integer.class
        );
        assertThat(appliedMigrations).isEqualTo(3);

        // 2. Verify 4 new 1-1 satellite tables exist and have equal row counts to parent tables
        Long ticketCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket", Long.class);
        Long ticketLocCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_location", Long.class);
        Long ticketAssCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_assessment", Long.class);
        assertThat(ticketCount).isGreaterThan(0);
        assertThat(ticketLocCount).isEqualTo(ticketCount);
        assertThat(ticketAssCount).isEqualTo(ticketCount);

        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Long.class);
        Long userProfCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profile", Long.class);
        Long userAddrCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_address", Long.class);
        assertThat(userCount).isGreaterThan(0);
        assertThat(userProfCount).isEqualTo(userCount);
        assertThat(userAddrCount).isEqualTo(userCount);

        // 3. Verify legacy columns were contracted from ticket and user_account
        Integer legacyTicketCols = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ticket' AND column_name IN ('location', 'address', 'location_method', 'priority', 'priority_reasoning', 'is_life_threatening', 'needs_manual_review', 'review_note', 'deadline')",
                Integer.class
        );
        assertThat(legacyTicketCols).isZero();

        Integer legacyUserCols = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'user_account' AND column_name IN ('role_id', 'department_id', 'full_name', 'phone_number', 'province', 'ward', 'address')",
                Integer.class
        );
        assertThat(legacyUserCols).isZero();

        // =========================================================================
        // 4. REAL END-TO-END HTTP API TESTS (JWT Filter -> Controller -> Service -> PostgreSQL)
        // =========================================================================
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(jwtAuthenticationFilter)
                .build();

        // 4.1. POST /api/v1/auth/login (Real HTTP Auth endpoint against user_account + user_profile)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.test@cityfix.vn\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        User adminUser = userRepository.findByEmailAndIsDeletedFalse("admin.test@cityfix.vn").orElseThrow();
        User managerUser = userRepository.findByEmailAndIsDeletedFalse("manager.gtvt@cityfix.vn").orElseThrow();
        User staffUser = userRepository.findByEmailAndIsDeletedFalse("staff.gtvt@cityfix.vn").orElseThrow();
        User residentUser = userRepository.findByEmailAndIsDeletedFalse("resident.an@cityfix.vn").orElseThrow();

        String adminToken = "Bearer " + tokenService.generateAccessToken(adminUser);
        String managerToken = "Bearer " + tokenService.generateAccessToken(managerUser);
        String staffToken = "Bearer " + tokenService.generateAccessToken(staffUser);
        String residentToken = "Bearer " + tokenService.generateAccessToken(residentUser);

        // 4.2. GET /api/v1/profile & PUT /api/v1/profile (ProfileController reading/updating user_profile & user_address)
        mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("resident.an@cityfix.vn"))
                .andExpect(jsonPath("$.data.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.ward").value("Phường Bến Nghé"));

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nguyễn Văn An\",\"phoneNumber\":\"0909000007\",\"province\":\"TP. Hồ Chí Minh\",\"ward\":\"Phường Bến Nghé\",\"address\":\"12 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn An"));

        // 4.3. GET /api/v1/admin/users & GET /api/v1/departments (AdminUserController & DepartmentController)
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userId").isNotEmpty());

        mockMvc.perform(get("/api/v1/departments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        // 4.4. GET /api/v1/admin/tickets & GET /api/v1/admin/tickets/{id} (AdminTicketController)
        Page<TicketResponse> filteredTickets = adminTicketService.getAllTickets(
                AdminTicketFilterRequest.builder().keyword("sụt lún").priority(Priority.HIGH).build(),
                PageRequest.of(0, 10)
        );
        assertThat(filteredTickets.getContent()).isNotEmpty();
        UUID sampleTicketId = filteredTickets.getContent().get(0).getId();

        mockMvc.perform(get("/api/v1/admin/tickets")
                        .header("Authorization", adminToken)
                        .param("keyword", "sụt lún")
                        .param("priority", "HIGH")
                        .param("province", "Hồ Chí Minh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/tickets/" + sampleTicketId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sampleTicketId.toString()))
                .andExpect(jsonPath("$.data.address").isNotEmpty())
                .andExpect(jsonPath("$.data.priority").value("HIGH"));

        // 4.5. GET /api/v1/admin/dashboard/summary & GET /api/v1/admin/analytics (AdminDashboardController)
        mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketSummary.totalTickets").isNumber())
                .andExpect(jsonPath("$.data.accountSummary.totalUsers").isNumber());

        mockMvc.perform(get("/api/v1/admin/analytics")
                        .header("Authorization", adminToken)
                        .param("groupBy", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalTickets").isNumber());

        // 4.6. Resident TicketController endpoints (my-reports, my-upvoted, in-progress, life-threatening, check-duplicates)
        mockMvc.perform(get("/api/v1/tickets/my-reports")
                        .header("Authorization", residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/tickets/my-upvoted")
                        .header("Authorization", residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/tickets/in-progress")
                        .header("Authorization", residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/tickets/life-threatening")
                        .header("Authorization", residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        List<SubCategory> subCategories = subCategoryRepository.findAll();
        if (!subCategories.isEmpty()) {
            UUID subCatId = subCategories.get(0).getSubCategoryId();
            mockMvc.perform(get("/api/v1/tickets/check-duplicates")
                            .header("Authorization", residentToken)
                            .param("latitude", "10.773100")
                            .param("longitude", "106.701200")
                            .param("subCategoryId", subCatId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        // 4.7. ManagerTicketController endpoints (/manager/tickets, /initiated, /resolved, /department, /departments)
        mockMvc.perform(get("/api/v1/manager/tickets")
                        .header("Authorization", managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/manager/tickets/initiated")
                        .header("Authorization", managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/manager/tickets/resolved")
                        .header("Authorization", managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/manager/tickets/department")
                        .header("Authorization", managerToken)
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/manager/tickets/departments")
                        .header("Authorization", managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 4.8. StaffTicketController endpoints (/staff/tickets/assigned)
        mockMvc.perform(get("/api/v1/staff/tickets/assigned")
                        .header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}


