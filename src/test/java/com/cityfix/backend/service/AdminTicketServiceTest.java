package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.RatingMapper;
import com.cityfix.backend.mapper.TicketHistoryMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.mapper.TicketStepMapper;
import com.cityfix.backend.model.dto.request.AdminTicketFilterRequest;
import com.cityfix.backend.model.dto.response.*;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.service.impl.AdminTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketStepRepository ticketStepRepository;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private TicketVoteRepository ticketVoteRepository;
    @Mock
    private RoutingRuleRepository routingRuleRepository;

    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private TicketStepMapper ticketStepMapper;
    @Mock
    private TicketHistoryMapper ticketHistoryMapper;
    @Mock
    private RatingMapper ratingMapper;

    @InjectMocks
    private AdminTicketServiceImpl adminTicketService;

    private UUID ticketId;
    private UUID userId;
    private UUID categoryId;
    private UUID subCategoryId;
    private UUID departmentId;
    private User reporter;
    private Category category;
    private SubCategory subCategory;
    private Department department;
    private Ticket ticket;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        subCategoryId = UUID.randomUUID();
        departmentId = UUID.randomUUID();

        reporter = User.builder()
                .userId(userId)
                .fullName("Nguyen Van A")
                .email("nguyenvana@example.com")
                .phoneNumber("0901234567")
                .build();

        category = Category.builder()
                .categoryId(categoryId)
                .name("Giao thông")
                .isActive(true)
                .build();

        subCategory = SubCategory.builder()
                .subCategoryId(subCategoryId)
                .name("Ổ gà")
                .category(category)
                .priority(Priority.HIGH)
                .isActive(true)
                .build();

        department = Department.builder()
                .departmentId(departmentId)
                .name("Sở Giao thông Vận tải")
                .build();

        Point point = geometryFactory.createPoint(new Coordinate(106.700806, 10.776889));

        ticket = Ticket.builder()
                .ticketId(ticketId)
                .user(reporter)
                .description("Ổ gà lớn gây nguy hiểm")
                .address("123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .location(point)
                .priority(Priority.HIGH)
                .status(TicketStatus.INITIATED)
                .isAnonymous(true)
                .isLifeThreatening(true)
                .needsManualReview(true)
                .createdAt(LocalDateTime.now().minusHours(5))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        TicketSubCategory tsc = TicketSubCategory.builder()
                .ticket(ticket)
                .subCategory(subCategory)
                .build();
        ticket.setSubCategories(List.of(tsc));
    }

    @Test
    @DisplayName("getAllTickets_withFiltersAndRoutingRuleEnrichment_returnsPagedTicketsWithUnmaskedCreator")
    @SuppressWarnings("unchecked")
    void getAllTickets_withFiltersAndRoutingRuleEnrichment_returnsPagedTicketsWithUnmaskedCreator() {
        Pageable pageable = PageRequest.of(0, 20);
        AdminTicketFilterRequest filter = AdminTicketFilterRequest.builder()
                .keyword("Ổ gà")
                .status(TicketStatus.INITIATED)
                .priority(Priority.HIGH)
                .unassigned(true)
                .build();

        TicketResponse mappedResponse = TicketResponse.builder()
                .id(ticketId)
                .description(ticket.getDescription())
                .status(TicketStatus.INITIATED)
                .isAnonymous(true)
                .build();

        RoutingRule routingRule = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(department)
                .province("TP. Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();

        when(ticketRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(ticket), pageable, 1));
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(mappedResponse);
        when(routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(routingRule));

        Page<TicketResponse> result = adminTicketService.getAllTickets(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        TicketResponse item = result.getContent().get(0);
        assertThat(item.getId()).isEqualTo(ticketId);
        // Admin always sees reporter identity even when isAnonymous = true
        assertThat(item.getCreatedBy()).isEqualTo(userId);
        assertThat(item.getCreatorFullName()).isEqualTo("Nguyen Van A");
        assertThat(item.getCreatorPhoneNumber()).isEqualTo("0901234567");
        // Enriched department from RoutingRule
        assertThat(item.getDepartmentId()).isEqualTo(departmentId);
        assertThat(item.getDepartmentName()).isEqualTo("Sở Giao thông Vận tải");
        verify(ticketRepository).findAllWithSubCategoriesByTicketIdIn(List.of(ticketId));
    }

    @Test
    @DisplayName("getAllTickets_routingRuleDoesNotMatchLocation_doesNotAssignArbitraryDepartment")
    @SuppressWarnings("unchecked")
    void getAllTickets_routingRuleDoesNotMatchLocation_doesNotAssignArbitraryDepartment() {
        Pageable pageable = PageRequest.of(0, 20);
        AdminTicketFilterRequest filter = AdminTicketFilterRequest.builder().build();

        TicketResponse mappedResponse = TicketResponse.builder()
                .id(ticketId)
                .description(ticket.getDescription())
                .status(TicketStatus.INITIATED)
                .build();

        // RoutingRule belongs to the same category, but for a completely different province/ward (Hà Nội)
        RoutingRule nonMatchingRule = RoutingRule.builder()
                .ruleId(UUID.randomUUID())
                .category(category)
                .department(department)
                .province("Thành phố Hà Nội")
                .ward("Phường Hàng Trống")
                .build();

        when(ticketRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(ticket), pageable, 1));
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(mappedResponse);
        when(routingRuleRepository.findByCategoryCategoryIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(nonMatchingRule));

        Page<TicketResponse> result = adminTicketService.getAllTickets(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        TicketResponse item = result.getContent().get(0);
        assertThat(item.getDepartmentId()).isNull();
        assertThat(item.getDepartmentName()).isNull();
    }

    @Test
    @DisplayName("getAllTickets_pageBeyondLastPage_returnsEmptyContentButPreservesTotalElements")
    @SuppressWarnings("unchecked")
    void getAllTickets_pageBeyondLastPage_returnsEmptyContentButPreservesTotalElements() {
        Pageable outOfRangePageable = PageRequest.of(5, 20);
        AdminTicketFilterRequest filter = AdminTicketFilterRequest.builder().build();

        when(ticketRepository.findAll(any(Specification.class), eq(outOfRangePageable)))
                .thenReturn(new PageImpl<>(List.of(), outOfRangePageable, 42L));

        Page<TicketResponse> result = adminTicketService.getAllTickets(filter, outOfRangePageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(42L);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("getTicketDetail_existingTicket_returnsFullAdminTicketDetailResponse")
    void getTicketDetail_existingTicket_returnsFullAdminTicketDetailResponse() {
        User staff = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Tran Van Staff")
                .department(department)
                .build();

        Step stepEntity = Step.builder()
                .stepId(UUID.randomUUID())
                .stepName("Khảo sát hiện trường")
                .build();

        TicketStep ticketStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .step(stepEntity)
                .assignee(staff)
                .status(AssignmentStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now().minusHours(3))
                .build();
        ticket.setSteps(List.of(ticketStep));

        TicketResponse mappedBase = TicketResponse.builder()
                .id(ticketId)
                .description(ticket.getDescription())
                .categories(List.of(new TicketCategoryResponse(categoryId, "Giao thông")))
                .imageUrls(List.of("https://res.cloudinary.com/demo/image1.jpg"))
                .departmentId(departmentId)
                .departmentName("Sở Giao thông Vận tải")
                .assigneeId(staff.getUserId())
                .assigneeName(staff.getFullName())
                .build();

        TicketStepResponse stepResponse = TicketStepResponse.builder()
                .id(ticketStep.getId())
                .stepId(stepEntity.getStepId())
                .stepName("Khảo sát hiện trường")
                .assigneeId(staff.getUserId())
                .assigneeName(staff.getFullName())
                .status(AssignmentStatus.IN_PROGRESS)
                .build();

        TicketHistory history = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .ticket(ticket)
                .actionNote("Tạo mới hồ sơ")
                .build();
        TicketHistoryResponse historyResponse = TicketHistoryResponse.builder()
                .historyId(history.getHistoryId())
                .ticketId(ticketId)
                .actionNote("Tạo mới hồ sơ")
                .build();

        Rating rating = Rating.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .user(reporter)
                .rate(5)
                .feedback("Xử lý tốt")
                .build();
        RatingResponse ratingResponse = RatingResponse.builder()
                .id(rating.getId())
                .ticketId(ticketId)
                .rate(5)
                .feedback("Xử lý tốt")
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(mappedBase);
        when(ticketStepMapper.toTicketStepResponseList(any())).thenReturn(List.of(stepResponse));
        when(ticketHistoryRepository.findByTicketTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of(history));
        when(ticketHistoryMapper.toTicketHistoryResponseList(List.of(history))).thenReturn(List.of(historyResponse));
        when(ratingRepository.findByTicket_TicketIdAndIsDeletedFalse(ticketId)).thenReturn(List.of(rating));
        when(ratingMapper.toResponseList(List.of(rating))).thenReturn(List.of(ratingResponse));
        when(ticketVoteRepository.countByTicket_TicketId(ticketId)).thenReturn(12L);

        AdminTicketDetailResponse detail = adminTicketService.getTicketDetail(ticketId);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(ticketId);
        assertThat(detail.getCreatorFullName()).isEqualTo("Nguyen Van A");
        assertThat(detail.getCreatorEmail()).isEqualTo("nguyenvana@example.com");
        assertThat(detail.getCreatorPhoneNumber()).isEqualTo("0901234567");
        assertThat(detail.getLatitude()).isEqualTo(10.776889);
        assertThat(detail.getLongitude()).isEqualTo(106.700806);
        assertThat(detail.getIsLifeThreatening()).isTrue();
        assertThat(detail.getNeedsManualReview()).isTrue();
        assertThat(detail.getUpvoteCount()).isEqualTo(12L);
        assertThat(detail.getSteps()).hasSize(1);
        assertThat(detail.getHistories()).hasSize(1);
        assertThat(detail.getRatings()).hasSize(1);
    }

    @Test
    @DisplayName("getTicketDetail_nonExistentId_throwsTicketNotFound")
    void getTicketDetail_nonExistentId_throwsTicketNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminTicketService.getTicketDetail(unknownId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }
}
