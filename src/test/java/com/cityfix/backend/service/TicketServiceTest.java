package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.model.dto.request.AssignTicketRequest;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.dto.response.TicketUpvoterResponse;
import com.cityfix.backend.model.dto.response.TicketUpvotesResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.LocationMethod;

import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
import com.cityfix.backend.mapper.SubCategoryMapper;
import com.cityfix.backend.service.impl.TicketServiceImpl;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketVoteRepository ticketVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDepartmentRepository userDepartmentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private com.cityfix.backend.repository.TicketSubCategoryRepository ticketSubCategoryRepository;

    @Mock
    private com.cityfix.backend.repository.SubCategoryRepository subCategoryRepository;

    @Mock
    private SubCategoryMapper subCategoryMapper;

    @Mock
    private ReportImageRepository reportImageRepository;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private ImageMapper imageMapper;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private com.cityfix.backend.repository.RoutingRuleRepository routingRuleRepository;

    @Mock
    private com.cityfix.backend.repository.DepartmentRepository departmentRepository;

    @Mock
    private com.cityfix.backend.repository.TicketStepRepository ticketStepRepository;

    @Mock
    private com.cityfix.backend.repository.StepRepository stepRepository;

    @Mock
    private com.cityfix.backend.repository.TicketHistoryRepository ticketHistoryRepository;

    @Mock
    private com.cityfix.backend.service.LocationService locationService;

    @Mock
    private com.cityfix.backend.mapper.TicketStepMapper ticketStepMapper;

    @Mock
    private com.cityfix.backend.mapper.TicketHistoryMapper ticketHistoryMapper;
    

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private com.cityfix.backend.service.EmailService emailService;

    @Mock
    private com.cityfix.backend.service.NotificationService notificationService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private org.springframework.security.core.context.SecurityContext securityContext;


    @Mock
    private org.springframework.security.core.Authentication authentication;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private String email;
    private User mockUser;
    private Category mockCategory;
    private CreateTicketRequest createRequest;
    private Ticket mockTicket;
    private TicketResponse mockResponse;
    private UUID categoryId;
    private UUID subCategoryId;

    @BeforeEach
    void setUp() {
        email = "resident@cityfix.com";
        categoryId = UUID.randomUUID();
        subCategoryId = UUID.randomUUID();

        mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail(email);

        org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getPrincipal()).thenReturn(mockUser);
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));

        mockCategory = Category.builder()
                .categoryId(categoryId)
                .name("Hạ tầng giao thông")
                .build();

        createRequest = new CreateTicketRequest();
        createRequest.setDescription("Mặt đường bị sụt hố sâu lớn");
        createRequest.setImageUrl("https://cloudinary.com/image.jpg");
        createRequest.setPriorityReasoning("Test reasoning");
        createRequest.setPriority(Priority.HIGH);
        createRequest.setLocationMethod(LocationMethod.AUTO_GPS);
        createRequest.setAddress("268 Lý Thường Kiệt, Q10");
        createRequest.setLatitude(10.762622);
        createRequest.setLongitude(106.660172);

        mockTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description(createRequest.getDescription())
                .priorityReasoning(createRequest.getPriorityReasoning())
                .priority(createRequest.getPriority())
                .locationMethod(createRequest.getLocationMethod())
                .address(createRequest.getAddress())
                .status(TicketStatus.INITIATED)
                .user(mockUser)
                .build();

        lenient().when(ticketMapper.toTicket(any(), any(), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            CreateTicketRequest req = invocation.getArgument(0);
            User u = invocation.getArgument(1);
            org.locationtech.jts.geom.Point loc = invocation.getArgument(2);
            TicketStatus st = invocation.getArgument(3);
            boolean needsRev = invocation.getArgument(4);

            return Ticket.builder()
                    .description(req.getDescription())
                    .priorityReasoning(req.getPriorityReasoning())
                    .priority(req.getPriority())
                    .locationMethod(req.getLocationMethod())
                    .address(req.getAddress())
                    .status(st)
                    .needsManualReview(needsRev)
                    .location(loc)
                    .user(u)
                    .isAnonymous(req.getIsAnonymous())
                    .build();
        });

        lenient().when(ticketHistoryMapper.toTicketHistory(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            User actor = invocation.getArgument(1);
            com.cityfix.backend.model.enums.ActionType action = invocation.getArgument(2);
            TicketStatus prev = invocation.getArgument(3);
            TicketStatus curr = invocation.getArgument(4);
            String note = invocation.getArgument(5);
            return com.cityfix.backend.model.entity.TicketHistory.builder()
                    .ticket(t)
                    .actor(actor)
                    .actionType(action)
                    .previousStatus(prev)
                    .currentStatus(curr)
                    .actionNote(note)
                    .build();
        });

        lenient().when(imageMapper.toReportImage(any(), any())).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            String url = invocation.getArgument(1);
            return com.cityfix.backend.model.entity.ReportImage.builder()
                    .ticket(t)
                    .imageUrl(url)
                    .build();
        });

        lenient().when(ticketStepMapper.toTicketStep(any(), any(), any(), any())).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            Step s = invocation.getArgument(1);
            User staff = invocation.getArgument(2);
            AssignmentStatus st = invocation.getArgument(3);
            return TicketStep.builder()
                    .ticket(t)
                    .step(s)
                    .assignee(staff)
                    .status(st)
                    .build();
        });

        mockResponse = TicketResponse.builder()
                .id(mockTicket.getTicketId())
                .description(mockTicket.getDescription())
                .imageUrls(List.of(createRequest.getImageUrl()))
                .priorityReasoning(createRequest.getPriorityReasoning())
                .priority(createRequest.getPriority())
                .locationMethod(createRequest.getLocationMethod())
                .address(createRequest.getAddress())
                .status(TicketStatus.INITIATED)
                .latitude(createRequest.getLatitude())
                .longitude(createRequest.getLongitude())
                .createdBy(mockUser.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ==========================================
    // 1. Create Ticket Service Tests
    // ==========================================

    @Test
    @DisplayName("createTicket_validData_returnsTicketResponse")
    void createTicket_validData_returnsTicketResponse() {
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).isEqualTo("Mặt đường bị sụt hố sâu lớn");
        assertThat(result.getCreatedBy()).isEqualTo(mockUser.getUserId());
        assertThat(result.getImageUrls()).containsExactly(createRequest.getImageUrl());
        assertThat(result.getLocationMethod()).isEqualTo(LocationMethod.AUTO_GPS);

        verify(ticketRepository, times(1)).save(any(Ticket.class));
        verify(reportImageRepository, times(1)).save(any());
        verify(ticketMapper, times(1)).toTicketResponse(any(Ticket.class));
    }

    @Test
    @DisplayName("createTicket_withMultipleImageUrls_persistsAllReportImagesAndReturnsThumbnail")
    void createTicket_withMultipleImageUrls_persistsAllReportImagesAndReturnsThumbnail() {
        createRequest.setImageUrl("https://cloudinary.com/fallback.jpg");
        createRequest.setImageUrls(List.of("https://cloudinary.com/img1.jpg", "https://cloudinary.com/img2.jpg"));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            return TicketResponse.builder()
                    .id(t.getTicketId())
                    .imageUrls(t.getImages().stream().map(ReportImage::getImageUrl).toList())
                    .build();
        });

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getImageUrls()).containsExactly("https://cloudinary.com/img1.jpg", "https://cloudinary.com/img2.jpg");
        verify(reportImageRepository, times(2)).save(any(ReportImage.class));
    }

    @Test
    @DisplayName("createTicket_withLegacyImageUrlOnly_convertsToImageList")
    void createTicket_withLegacyImageUrlOnly_convertsToImageList() {
        createRequest.setImageUrl("https://cloudinary.com/legacy.jpg");
        createRequest.setImageUrls(null);

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            return TicketResponse.builder()
                    .id(t.getTicketId())
                    .imageUrls(t.getImages().stream().map(ReportImage::getImageUrl).toList())
                    .build();
        });

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getImageUrls()).containsExactly("https://cloudinary.com/legacy.jpg");
        verify(reportImageRepository, times(1)).save(any(ReportImage.class));
    }

    @Test
    @DisplayName("createTicket_imageUrlsWithWhitespace_trimsUrlsBeforeSaving")
    void createTicket_imageUrlsWithWhitespace_trimsUrlsBeforeSaving() {
        createRequest.setImageUrl(null);
        createRequest.setImageUrls(List.of("   https://cloudinary.com/img1.jpg   ", "   ", "https://cloudinary.com/img2.jpg "));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            return TicketResponse.builder()
                    .id(t.getTicketId())
                    .imageUrls(t.getImages().stream().map(ReportImage::getImageUrl).toList())
                    .build();
        });

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getImageUrls()).containsExactly("https://cloudinary.com/img1.jpg", "https://cloudinary.com/img2.jpg");
        verify(reportImageRepository, times(2)).save(argThat(img ->
                img.getImageUrl().equals("https://cloudinary.com/img1.jpg") || img.getImageUrl().equals("https://cloudinary.com/img2.jpg")
        ));
    }

    @Test
    @DisplayName("createTicket_withNullImageUrlsAndNullImageUrl_createsTicketWithoutReportImages")
    void createTicket_withNullImageUrlsAndNullImageUrl_createsTicketWithoutReportImages() {
        createRequest.setImageUrl(null);
        createRequest.setImageUrls(null);

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            return TicketResponse.builder()
                    .id(t.getTicketId())
                    .imageUrls(t.getImages().stream().map(ReportImage::getImageUrl).toList())
                    .build();
        });

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getImageUrls()).isEmpty();
        assertThat(mockTicket.getImageUrl()).isNull();
        verify(reportImageRepository, never()).save(any(ReportImage.class));
    }

    @Test
    @DisplayName("createTicket_recurringIncidentCountIs2_triggersAlertFor3rdOccurrence")
    void createTicket_recurringIncidentCountIs2_triggersAlertFor3rdOccurrence() {
        UUID subCatId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subCatId));
        createRequest.setLatitude(10.762622);
        createRequest.setLongitude(106.660172);

        SubCategory sub = SubCategory.builder().subCategoryId(subCatId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subCatId)).thenReturn(Optional.of(sub));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        when(ticketRepository.countRecurringCompletedTickets(eq(10.762622), eq(106.660172), any(), any()))
                .thenReturn(2L);
        UUID oldTicket1 = UUID.randomUUID();
        UUID oldTicket2 = UUID.randomUUID();
        when(ticketRepository.findRecurringCompletedTicketIds(eq(10.762622), eq(106.660172), any(), any()))
                .thenReturn(List.of(oldTicket1, oldTicket2));

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        verify(ticketRepository).countRecurringCompletedTickets(eq(10.762622), eq(106.660172), any(), any());
        verify(ticketRepository).findRecurringCompletedTicketIds(eq(10.762622), eq(106.660172), any(), any());

        ArgumentCaptor<com.cityfix.backend.event.RecurringTicketEvent> eventCaptor =
                ArgumentCaptor.forClass(com.cityfix.backend.event.RecurringTicketEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        com.cityfix.backend.event.RecurringTicketEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getOccurrenceCount()).isEqualTo(3L);
        assertThat(publishedEvent.getRecurringTicketIds()).containsExactly(oldTicket1, oldTicket2);
    }

    @Test
    @DisplayName("createTicket_recurringIncidentCountLessThan2_doesNotTriggerAlert")
    void createTicket_recurringIncidentCountLessThan2_doesNotTriggerAlert() {
        UUID subCatId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subCatId));
        createRequest.setLatitude(10.762622);
        createRequest.setLongitude(106.660172);

        SubCategory sub = SubCategory.builder().subCategoryId(subCatId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subCatId)).thenReturn(Optional.of(sub));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        when(ticketRepository.countRecurringCompletedTickets(eq(10.762622), eq(106.660172), any(), any()))
                .thenReturn(1L);

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        verify(ticketRepository).countRecurringCompletedTickets(eq(10.762622), eq(106.660172), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createTicket_recurringEngineThrowsException_doesNotFailTicketCreation")
    void createTicket_recurringEngineThrowsException_doesNotFailTicketCreation() {
        UUID subCatId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subCatId));
        createRequest.setLatitude(10.762622);
        createRequest.setLongitude(106.660172);

        SubCategory sub = SubCategory.builder().subCategoryId(subCatId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subCatId)).thenReturn(Optional.of(sub));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        when(ticketRepository.countRecurringCompletedTickets(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("PostGIS database timeout"));

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        verify(ticketRepository, times(1)).save(any(Ticket.class));
        verify(ticketMapper, times(1)).toTicketResponse(any(Ticket.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createTicket_accountNotFound_throwsAccountNotFoundAppException")
    void createTicket_accountNotFound_throwsAccountNotFoundAppException() {
        when(authentication.getPrincipal()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_unauthenticated_throwsUnauthenticatedAppException")
    void createTicket_unauthenticated_throwsUnauthenticatedAppException() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_multiSubCategory_triggersManualReviewAndBypassesAutoRouting")
    void createTicket_multiSubCategory_triggersManualReviewAndBypassesAutoRouting() {
        UUID subId1 = UUID.randomUUID();
        UUID subId2 = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId1, subId2));
        createRequest.setPriority(Priority.LOW);

        createRequest.setConfidenceScore(95.0);

        SubCategory sub1 = SubCategory.builder().subCategoryId(subId1).category(mockCategory).isActive(true).build();
        SubCategory sub2 = SubCategory.builder().subCategoryId(subId2).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subId1)).thenReturn(Optional.of(sub1));
        when(subCategoryRepository.findById(subId2)).thenReturn(Optional.of(sub2));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        // Since isMultiSubCategory is true, auto-routing is bypassed completely
        verifyNoInteractions(locationService);
        verifyNoInteractions(routingRuleRepository);
        verify(ticketRepository).save(argThat(t -> Boolean.TRUE.equals(t.getNeedsManualReview()) && t.getStatus() == TicketStatus.INITIATED));
    }

    @Test
    @DisplayName("createTicket_subCategoryIdsContainNull_throwsINVALID_REQUEST")
    void createTicket_subCategoryIdsContainNull_throwsINVALID_REQUEST() {
        createRequest.setSubCategoryIds(java.util.Arrays.asList(UUID.randomUUID(), null));

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_subCategoryIdsContainDuplicates_throwsINVALID_REQUEST")
    void createTicket_subCategoryIdsContainDuplicates_throwsINVALID_REQUEST() {
        UUID dupId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(dupId, dupId));

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_subCategoryNotFound_throwsSUBCATEGORY_NOT_FOUND")
    void createTicket_subCategoryNotFound_throwsSUBCATEGORY_NOT_FOUND() {
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));

        when(subCategoryRepository.findById(subId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUBCATEGORY_NOT_FOUND);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_subCategoryInactive_throwsINVALID_REQUEST")
    void createTicket_subCategoryInactive_throwsINVALID_REQUEST() {
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));

        SubCategory inactiveSub = SubCategory.builder()
                .subCategoryId(subId)
                .category(mockCategory)
                .isActive(false)
                .build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(inactiveSub));

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_validSubCategories_persistsTicketSubCategoriesAndStepsWhenRouted")
    void createTicket_validSubCategories_persistsTicketSubCategoriesAndStepsWhenRouted() {
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));
        createRequest.setPriority(Priority.LOW);

        createRequest.setConfidenceScore(95.0);

        SubCategory validSub = SubCategory.builder()
                .subCategoryId(subId)
                .category(mockCategory)
                .isActive(true)
                .build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(validSub));

        UUID deptId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(deptId);
        com.cityfix.backend.model.entity.RoutingRule rule = com.cityfix.backend.model.entity.RoutingRule.builder()
                .department(dept)
                .category(mockCategory)
                .build();
        when(routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(any(), any(), any()))
                .thenReturn(Optional.of(rule));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(deptId)).thenReturn(Optional.of(repStaff));

        Step stepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(validSub).stepName("Bước 1").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subId)).thenReturn(List.of(stepEntity));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = ticketService.createTicket(createRequest);

        assertThat(response).isNotNull();
        verify(ticketSubCategoryRepository, atLeastOnce()).save(any());
        verify(ticketStepRepository, atLeastOnce()).save(argThat(ts -> ts != null && ts.getAssignee() != null && !ts.isDeleted()));
    }

    @Test
    @DisplayName("createTicket_withGpsBoundary_usesLocationBoundaryForRouting")
    void createTicket_withGpsBoundary_usesLocationBoundaryForRouting() {
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));
        createRequest.setPriority(Priority.LOW);

        createRequest.setConfidenceScore(95.0);
        createRequest.setLatitude(10.776530);
        createRequest.setLongitude(106.700980);

        SubCategory validSub = SubCategory.builder().subCategoryId(subId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(validSub));

        when(locationService.getBoundaryFromCoordinates(10.776530, 106.700980))
                .thenReturn(com.cityfix.backend.model.dto.response.LocationBoundary.builder()
                        .province("TP Hồ Chí Minh")
                        .ward("Phường Bến Nghé")
                        .build());

        UUID deptId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(deptId);
        com.cityfix.backend.model.entity.RoutingRule rule = com.cityfix.backend.model.entity.RoutingRule.builder()
                .department(dept)
                .category(mockCategory)
                .province("TP Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();
        when(routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, "TP Hồ Chí Minh", "Phường Bến Nghé"))
                .thenReturn(Optional.of(rule));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(deptId)).thenReturn(Optional.of(repStaff));

        Step stepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(validSub).stepName("Bước 1").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subId)).thenReturn(List.of(stepEntity));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = ticketService.createTicket(createRequest);

        assertThat(response).isNotNull();
        verify(locationService).getBoundaryFromCoordinates(10.776530, 106.700980);
        verify(routingRuleRepository).findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, "TP Hồ Chí Minh", "Phường Bến Nghé");
    }

    @Test
    @DisplayName("createTicket_gpsNotFound_fallsBackToAddressString")
    void createTicket_gpsNotFound_fallsBackToAddressString() {
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));
        createRequest.setPriority(Priority.LOW);

        createRequest.setConfidenceScore(95.0);
        createRequest.setLatitude(10.776530);
        createRequest.setLongitude(106.700980);
        createRequest.setAddress("Số 12, Đường Lê Lợi, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh");

        SubCategory validSub = SubCategory.builder().subCategoryId(subId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(validSub));

        // GPS returns null -> fallback
        when(locationService.getBoundaryFromCoordinates(10.776530, 106.700980)).thenReturn(null);

        UUID deptId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(deptId);
        com.cityfix.backend.model.entity.RoutingRule rule = com.cityfix.backend.model.entity.RoutingRule.builder()
                .department(dept)
                .category(mockCategory)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();
        when(routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, "Thành phố Hồ Chí Minh", "Phường Bến Nghé"))
                .thenReturn(Optional.of(rule));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(deptId)).thenReturn(Optional.of(repStaff));

        Step stepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(validSub).stepName("Bước 1").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subId)).thenReturn(List.of(stepEntity));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = ticketService.createTicket(createRequest);

        assertThat(response).isNotNull();
        verify(routingRuleRepository).findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, "Thành phố Hồ Chí Minh", "Phường Bến Nghé");
    }

    // ==========================================
    // 2. Upload Image & Video Service Tests
    // ==========================================

    @Test
    @DisplayName("uploadImage_singleValidImage_success")
    void uploadImage_singleValidImage_success() throws IOException {
        MockMultipartFile validImage = new MockMultipartFile(
                "files",
                "report_image.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> mockCloudinaryResponse = Map.of(
                "public_id", "cityfix/reports/img_123",
                "secure_url", "https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_123.jpg",
                "format", "jpg",
                "width", 1920,
                "height", 1080,
                "bytes", 1024L,
                "created_at", "2026-09-11T12:00:00Z"
        );

        ImageUploadResponse expectedResponse = ImageUploadResponse.builder()
                .publicId("cityfix/reports/img_123")
                .secureUrl("https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_123.jpg")
                .format("jpg")
                .width(1920)
                .height(1080)
                .bytes(1024L)
                .createdAt("2026-09-11T12:00:00Z")
                .build();

        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(mockCloudinaryResponse);
        when(imageMapper.toImageUploadResponse(mockCloudinaryResponse)).thenReturn(expectedResponse);

        List<ImageUploadResponse> actualResponses = ticketService.uploadImage(List.of(validImage), "cityfix/reports");

        assertThat(actualResponses).isNotNull().hasSize(1);
        assertThat(actualResponses.get(0).getPublicId()).isEqualTo("cityfix/reports/img_123");
        assertThat(actualResponses.get(0).getSecureUrl()).contains("cityfix/reports/img_123.jpg");

        verify(uploader, times(1)).upload(any(byte[].class), argThat(map -> "auto".equals(map.get("resource_type"))));
        verify(imageMapper, times(1)).toImageUploadResponse(mockCloudinaryResponse);
    }

    @Test
    @DisplayName("uploadImage_multipleFilesImageAndVideo_success")
    void uploadImage_multipleFilesImageAndVideo_success() throws IOException {
        MockMultipartFile imageFile = new MockMultipartFile(
                "files",
                "photo.png",
                "image/png",
                "image content".getBytes()
        );
        MockMultipartFile videoFile = new MockMultipartFile(
                "files",
                "clip.mp4",
                "video/mp4",
                "video content".getBytes()
        );

        when(cloudinary.uploader()).thenReturn(uploader);

        Map<String, Object> imgCloudinaryResponse = Map.of(
                "public_id", "cityfix/reports/img_456",
                "secure_url", "https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_456.png",
                "format", "png"
        );
        Map<String, Object> videoCloudinaryResponse = Map.of(
                "public_id", "cityfix/reports/vid_789",
                "secure_url", "https://res.cloudinary.com/cityfix/video/upload/v1/cityfix/reports/vid_789.mp4",
                "format", "mp4"
        );

        ImageUploadResponse imgMapped = ImageUploadResponse.builder().publicId("cityfix/reports/img_456").format("png").build();
        ImageUploadResponse videoMapped = ImageUploadResponse.builder().publicId("cityfix/reports/vid_789").format("mp4").build();

        when(uploader.upload(any(byte[].class), any(Map.class))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            if (new String(bytes).contains("image")) {
                return imgCloudinaryResponse;
            } else {
                return videoCloudinaryResponse;
            }
        });

        when(imageMapper.toImageUploadResponse(imgCloudinaryResponse)).thenReturn(imgMapped);
        when(imageMapper.toImageUploadResponse(videoCloudinaryResponse)).thenReturn(videoMapped);

        List<ImageUploadResponse> results = ticketService.uploadImage(List.of(imageFile, videoFile), "cityfix/reports");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ImageUploadResponse::getPublicId)
                .containsExactlyInAnyOrder("cityfix/reports/img_456", "cityfix/reports/vid_789");

        verify(uploader, times(2)).upload(any(byte[].class), argThat(map -> "auto".equals(map.get("resource_type"))));
    }

    @Test
    @DisplayName("uploadImage_fileCountExceeded_throwsAppException")
    void uploadImage_fileCountExceeded_throwsAppException() {
        List<MultipartFile> eightFiles = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> (MultipartFile) new MockMultipartFile("files", "img" + i + ".png", "image/png", "data".getBytes()))
                .toList();

        assertThatThrownBy(() -> ticketService.uploadImage(eightFiles, "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_COUNT_EXCEEDED);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_invalidFormat_throwsAppException")
    void uploadImage_invalidFormat_throwsAppException() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "files",
                "document.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        assertThatThrownBy(() -> ticketService.uploadImage(List.of(invalidFile), "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_imageExceedsMaxSize_throwsAppException")
    void uploadImage_imageExceedsMaxSize_throwsAppException() {
        byte[] largeImageContent = new byte[11 * 1024 * 1024]; // 11MB (> 10MB limit)
        MockMultipartFile largeImage = new MockMultipartFile(
                "files",
                "huge_photo.png",
                "image/png",
                largeImageContent
        );

        assertThatThrownBy(() -> ticketService.uploadImage(List.of(largeImage), "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_videoExceedsMaxSize_throwsAppException")
    void uploadImage_videoExceedsMaxSize_throwsAppException() {
        byte[] largeVideoContent = new byte[51 * 1024 * 1024]; // 51MB (> 50MB limit)
        MockMultipartFile largeVideo = new MockMultipartFile(
                "files",
                "huge_clip.mp4",
                "video/mp4",
                largeVideoContent
        );

        assertThatThrownBy(() -> ticketService.uploadImage(List.of(largeVideo), "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_cloudinaryIOException_throwsAppException")
    void uploadImage_cloudinaryIOException_throwsAppException() throws IOException {
        MockMultipartFile validFile = new MockMultipartFile(
                "files",
                "report_image.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> ticketService.uploadImage(List.of(validFile), "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);

        verify(uploader, times(1)).upload(any(byte[].class), any(Map.class));
    }

    // ==========================================
    // 3. Duplicate Ticket Detection Tests
    // ==========================================

    @Test
    @DisplayName("findDuplicateTickets_validCoordinates_returnsMappedDuplicatesList")
    void findDuplicateTickets_validCoordinates_returnsMappedDuplicatesList() {
        Double latitude = 10.762622;
        Double longitude = 106.660172;

        DuplicateTicketProjection mockProjection = mock(DuplicateTicketProjection.class);

        DuplicateTicketResponse mockDuplicateResponse = DuplicateTicketResponse.builder()
                .ticketId(UUID.randomUUID())
                .description("Hố sâu trên đường Lý Thường Kiệt")
                .address("268 Lý Thường Kiệt, Q10")
                .status("INITIATED")
                .categoryName("Hạ tầng giao thông")
                .latitude(10.762700)
                .longitude(106.660250)
                .distanceMeters(18.5)
                .imageUrl("https://cloudinary.com/image.jpg")
                .createdAt(LocalDateTime.now())
                .build();

        when(ticketRepository.findDuplicateTickets(latitude, longitude, subCategoryId))
                .thenReturn(List.of(mockProjection));
        when(ticketMapper.toDuplicateTicketResponseList(List.of(mockProjection)))
                .thenReturn(List.of(mockDuplicateResponse));

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(latitude, longitude, subCategoryId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicketId()).isEqualTo(mockDuplicateResponse.getTicketId());
        assertThat(result.get(0).getDistanceMeters()).isEqualTo(18.5);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cloudinary.com/image.jpg");

        verify(ticketRepository, times(1)).findDuplicateTickets(latitude, longitude, subCategoryId);
    }

    @Test
    @DisplayName("findDuplicateTickets_missingLatitude_throwsValidationAppException")
    void findDuplicateTickets_missingLatitude_throwsValidationAppException() {
        assertThatThrownBy(() -> ticketService.findDuplicateTickets(null, 106.660172, subCategoryId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(ticketRepository, never()).findDuplicateTickets(any(), any(), any());
    }

    @Test
    @DisplayName("findDuplicateTickets_missingSubCategoryId_throwsValidationAppException")
    void findDuplicateTickets_missingSubCategoryId_throwsValidationAppException() {
        assertThatThrownBy(() -> ticketService.findDuplicateTickets(10.762622, 106.660172, null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(ticketRepository, never()).findDuplicateTickets(any(), any(), any());
    }

    @Test
    @DisplayName("findDuplicateTickets_coordinatesOver200mAway_returnsEmptyList")
    void findDuplicateTickets_coordinatesOver200mAway_returnsEmptyList() {
        Double farLat = 10.780000;
        Double farLng = 106.680000;
        when(ticketRepository.findDuplicateTickets(farLat, farLng, subCategoryId))
                .thenReturn(Collections.emptyList());
        when(ticketMapper.toDuplicateTicketResponseList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(farLat, farLng, subCategoryId);

        assertThat(result).isNotNull().isEmpty();
        verify(ticketRepository).findDuplicateTickets(farLat, farLng, subCategoryId);
    }

    @Test
    @DisplayName("findDuplicateTickets_boundaryExactly50m_returnsMatchingDuplicatesList")
    void findDuplicateTickets_boundaryExactly50m_returnsMatchingDuplicatesList() {
        Double lat = 10.763071;
        Double lng = 106.660172;
        DuplicateTicketProjection boundaryProjection = mock(DuplicateTicketProjection.class);
        DuplicateTicketResponse boundaryResponse = DuplicateTicketResponse.builder()
                .ticketId(UUID.randomUUID())
                .status("ASSIGNED")
                .distanceMeters(50.0)
                .build();

        when(ticketRepository.findDuplicateTickets(lat, lng, subCategoryId))
                .thenReturn(List.of(boundaryProjection));
        when(ticketMapper.toDuplicateTicketResponseList(List.of(boundaryProjection)))
                .thenReturn(List.of(boundaryResponse));

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(lat, lng, subCategoryId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceMeters()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("findDuplicateTickets_coordinatesJustOutside50mRadius_returnsEmptyList")
    void findDuplicateTickets_coordinatesJustOutside50mRadius_returnsEmptyList() {
        Double lat = 10.763090;
        Double lng = 106.660172;
        when(ticketRepository.findDuplicateTickets(lat, lng, subCategoryId))
                .thenReturn(Collections.emptyList());
        when(ticketMapper.toDuplicateTicketResponseList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(lat, lng, subCategoryId);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findDuplicateTickets_within50mButDifferentSubCategory_returnsEmptyList")
    void findDuplicateTickets_within50mButDifferentSubCategory_returnsEmptyList() {
        UUID differentSubCatId = UUID.randomUUID();
        when(ticketRepository.findDuplicateTickets(10.762622, 106.660172, differentSubCatId))
                .thenReturn(Collections.emptyList());
        when(ticketMapper.toDuplicateTicketResponseList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(10.762622, 106.660172, differentSubCatId);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findDuplicateTickets_ticketWithMultipleSubCategoriesMatching_returnsDuplicatesList")
    void findDuplicateTickets_ticketWithMultipleSubCategoriesMatching_returnsDuplicatesList() {
        DuplicateTicketProjection multiSubProjection = mock(DuplicateTicketProjection.class);
        DuplicateTicketResponse multiSubResponse = DuplicateTicketResponse.builder()
                .ticketId(UUID.randomUUID())
                .status("IN_PROGRESS")
                .distanceMeters(12.0)
                .build();

        when(ticketRepository.findDuplicateTickets(10.762622, 106.660172, subCategoryId))
                .thenReturn(List.of(multiSubProjection));
        when(ticketMapper.toDuplicateTicketResponseList(List.of(multiSubProjection)))
                .thenReturn(List.of(multiSubResponse));

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(10.762622, 106.660172, subCategoryId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("findDuplicateTickets_closedCompletedOrCancelledTicketsOnly_returnsEmptyList")
    void findDuplicateTickets_closedCompletedOrCancelledTicketsOnly_returnsEmptyList() {
        when(ticketRepository.findDuplicateTickets(10.762622, 106.660172, subCategoryId))
                .thenReturn(Collections.emptyList());
        when(ticketMapper.toDuplicateTicketResponseList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(10.762622, 106.660172, subCategoryId);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findDuplicateTickets_rejectedTicketsOnly_returnsEmptyList")
    void findDuplicateTickets_rejectedTicketsOnly_returnsEmptyList() {
        when(ticketRepository.findDuplicateTickets(10.762650, 106.660180, subCategoryId))
                .thenReturn(Collections.emptyList());
        when(ticketMapper.toDuplicateTicketResponseList(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(10.762650, 106.660180, subCategoryId);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findDuplicateTickets_multipleActiveStatusesWithinRadius_returnsAllMatchingDuplicates")
    void findDuplicateTickets_multipleActiveStatusesWithinRadius_returnsAllMatchingDuplicates() {
        DuplicateTicketProjection p1 = mock(DuplicateTicketProjection.class);
        DuplicateTicketProjection p2 = mock(DuplicateTicketProjection.class);
        DuplicateTicketResponse r1 = DuplicateTicketResponse.builder().ticketId(UUID.randomUUID()).status("INITIATED").distanceMeters(10.0).build();
        DuplicateTicketResponse r2 = DuplicateTicketResponse.builder().ticketId(UUID.randomUUID()).status("IN_PROGRESS").distanceMeters(25.0).build();

        when(ticketRepository.findDuplicateTickets(10.762622, 106.660172, subCategoryId))
                .thenReturn(List.of(p1, p2));
        when(ticketMapper.toDuplicateTicketResponseList(List.of(p1, p2)))
                .thenReturn(List.of(r1, r2));

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(10.762622, 106.660172, subCategoryId);

        assertThat(result).hasSize(2);
    }

    // ==========================================
    // 4. Admin Ticket Management Tests
    // ==========================================

    @Test
    @DisplayName("getInitiatedTickets_returnsPageOfTickets")
    void getInitiatedTickets_returnsPageOfTickets() {
        org.springframework.data.domain.Page<Ticket> page = new org.springframework.data.domain.PageImpl<>(List.of(mockTicket));
        when(ticketRepository.findByStatus(eq(TicketStatus.INITIATED), any())).thenReturn(page);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getInitiatedTickets(org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        verify(ticketRepository, times(1)).findByStatus(eq(TicketStatus.INITIATED), any());
    }

    @Test
    @DisplayName("getMyReportedTickets_returnsPageOfTickets")
    void getMyReportedTickets_returnsPageOfTickets() {
        org.springframework.data.domain.Page<Ticket> page = new org.springframework.data.domain.PageImpl<>(List.of(mockTicket));
        when(ticketRepository.findByUserId(eq(mockUser.getUserId()), any())).thenReturn(page);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getMyReportedTickets(org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        verify(ticketRepository, times(1)).findByUserId(eq(mockUser.getUserId()), any());
    }

    @Test
    @DisplayName("getMyReportedTickets_emptyResult_returnsEmptyPage")
    void getMyReportedTickets_emptyResult_returnsEmptyPage() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Ticket> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, 0);
        when(ticketRepository.findByUserId(eq(mockUser.getUserId()), eq(pageable))).thenReturn(emptyPage);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getMyReportedTickets(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(ticketRepository).findByUserId(mockUser.getUserId(), pageable);
        verify(ticketMapper, never()).toTicketResponse(any());
    }

    @Test
    @DisplayName("getMyReportedTickets_negativePageIndex_throwsIllegalArgumentException")
    void getMyReportedTickets_negativePageIndex_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ticketService.getMyReportedTickets(org.springframework.data.domain.PageRequest.of(-1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page index must not be less than zero");

        verify(ticketRepository, never()).findByUserId(any(), any());
    }

    @Test
    @DisplayName("getMyReportedTickets_unauthenticated_throwsUnauthenticated")
    void getMyReportedTickets_unauthenticated_throwsUnauthenticated() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> ticketService.getMyReportedTickets(pageable))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verify(ticketRepository, never()).findByUserId(any(), any());
    }

    @Test
    @DisplayName("createTicket_noRoutingRuleMatches_setsInitiatedAndNeedsManualReview")
    void createTicket_noRoutingRuleMatches_setsInitiatedAndNeedsManualReview() {
        createRequest.setPriority(Priority.LOW);

        createRequest.setConfidenceScore(95.0);
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));
        SubCategory validSub = SubCategory.builder().subCategoryId(subId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(validSub));

        when(routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        verify(ticketRepository, times(1)).save(argThat(t ->
                t.getStatus() == TicketStatus.INITIATED && Boolean.TRUE.equals(t.getNeedsManualReview())
        ));
        verify(ticketHistoryRepository, times(1)).save(argThat(h ->
                h.getActionType() == com.cityfix.backend.model.enums.ActionType.ROUTED &&
                h.getCurrentStatus() == TicketStatus.INITIATED
        ));
    }

    // ==========================================
    // 5. Re-route & Staff Ticket List Tests
    // ==========================================

    @Test
    @DisplayName("createTicket_anonymous_setsIsAnonymousTrue")
    void createTicket_anonymous_setsIsAnonymousTrue() {
        createRequest.setIsAnonymous(true);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        verify(ticketRepository, times(1)).save(argThat(t -> Boolean.TRUE.equals(t.getIsAnonymous())));
    }

    @Test
    @DisplayName("rerouteTicket_validRequest_success")
    void rerouteTicket_validRequest_success() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);

        UUID targetDeptId = UUID.randomUUID();
        com.cityfix.backend.model.dto.request.RerouteTicketRequest rerouteRequest =
                com.cityfix.backend.model.dto.request.RerouteTicketRequest.builder()
                        .targetDepartmentId(targetDeptId)
                        .reason("Không thuộc thẩm quyền xử lý")
                        .build();

        mockTicket.setStatus(TicketStatus.ASSIGNED);

        SubCategory subCat = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .name("Đèn đường")
                .build();
        TicketSubCategory tsc = TicketSubCategory.builder()
                .ticket(mockTicket)
                .subCategory(subCat)
                .build();
        mockTicket.setSubCategories(new ArrayList<>(List.of(tsc)));

        Step mockStepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(subCat).stepName("Sửa").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCat.getSubCategoryId())).thenReturn(List.of(mockStepEntity));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(targetDeptId)).thenReturn(Optional.of(repStaff));

        TicketStep activeStep = TicketStep.builder()
                .ticket(mockTicket)
                .step(mockStepEntity)
                .status(AssignmentStatus.ASSIGNED)
                .build();
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(eq(mockTicket.getTicketId()), anyList()))
                .thenReturn(List.of(activeStep));

        com.cityfix.backend.model.entity.Department targetDept = new com.cityfix.backend.model.entity.Department();
        targetDept.setDepartmentId(targetDeptId);
        targetDept.setName("Phòng B");

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(departmentRepository.findById(targetDeptId)).thenReturn(Optional.of(targetDept));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = ticketService.rerouteTicket(mockTicket.getTicketId(), rerouteRequest);

        assertThat(response).isNotNull();
        verify(ticketHistoryRepository, times(1)).save(any());
        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    @DisplayName("rerouteTicket_userNotAdmin_throwsFORBIDDEN")
    void rerouteTicket_userNotAdmin_throwsFORBIDDEN() {
        Role residentRole = new Role(UUID.randomUUID(), "RESIDENT");
        mockUser.setRole(residentRole);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        com.cityfix.backend.model.dto.request.RerouteTicketRequest rerouteRequest =
                com.cityfix.backend.model.dto.request.RerouteTicketRequest.builder()
                        .targetDepartmentId(UUID.randomUUID())
                        .reason("Tự động chuyển")
                        .build();

        assertThatThrownBy(() -> ticketService.rerouteTicket(mockTicket.getTicketId(), rerouteRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("rerouteTicket_resolvedTicket_throwsTICKET_INVALID_STATUS")
    void rerouteTicket_resolvedTicket_throwsTICKET_INVALID_STATUS() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);
        mockTicket.setStatus(TicketStatus.RESOLVED);

        com.cityfix.backend.model.dto.request.RerouteTicketRequest rerouteRequest =
                com.cityfix.backend.model.dto.request.RerouteTicketRequest.builder()
                        .targetDepartmentId(UUID.randomUUID())
                        .reason("Chuyển nhầm")
                        .build();

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.rerouteTicket(mockTicket.getTicketId(), rerouteRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_INVALID_STATUS);
    }

    @Test
    @DisplayName("rerouteTicket_nonExistentTargetDepartment_throwsDEPARTMENT_NOT_FOUND")
    void rerouteTicket_nonExistentTargetDepartment_throwsDEPARTMENT_NOT_FOUND() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);
        mockTicket.setStatus(TicketStatus.ASSIGNED);

        UUID unknownDeptId = UUID.randomUUID();
        com.cityfix.backend.model.dto.request.RerouteTicketRequest rerouteRequest =
                com.cityfix.backend.model.dto.request.RerouteTicketRequest.builder()
                        .targetDepartmentId(unknownDeptId)
                        .reason("Chuyển sang phòng ban khác")
                        .build();

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(departmentRepository.findById(unknownDeptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.rerouteTicket(mockTicket.getTicketId(), rerouteRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("getAssignedTicketsForStaff_validStaff_returnsTicketWithStaffLinkedStepsOnly")
    void getAssignedTicketsForStaff_validStaff_returnsTicketWithStaffLinkedStepsOnly() {
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(UUID.randomUUID());
        dept.setName("Phòng Giao thông");
        mockUser.setDepartment(dept);

        User otherStaff = new User();
        otherStaff.setUserId(UUID.randomUUID());

        LocalDateTime now = LocalDateTime.now();
        com.cityfix.backend.model.entity.TicketStep myStep2 =
                com.cityfix.backend.model.entity.TicketStep.builder()
                        .id(UUID.randomUUID())
                        .ticket(mockTicket)
                        .assignee(mockUser)
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.IN_PROGRESS)
                        .createdAt(now.plusSeconds(1))
                        .build();

        com.cityfix.backend.model.entity.TicketStep myStep1 =
                com.cityfix.backend.model.entity.TicketStep.builder()
                        .id(UUID.randomUUID())
                        .ticket(mockTicket)
                        .assignee(mockUser)
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED)
                        .createdAt(now)
                        .build();

        com.cityfix.backend.model.entity.TicketStep otherStaffStep =
                com.cityfix.backend.model.entity.TicketStep.builder()
                        .id(UUID.randomUUID())
                        .ticket(mockTicket)
                        .assignee(otherStaff)
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED)
                        .build();

        mockTicket.setSteps(new ArrayList<>(List.of(myStep2, otherStaffStep, myStep1)));

        com.cityfix.backend.model.dto.response.TicketStepResponse stepRes1 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder()
                        .id(myStep1.getId())
                        .assigneeId(mockUser.getUserId())
                        .build();
        com.cityfix.backend.model.dto.response.TicketStepResponse stepRes2 =
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder()
                        .id(myStep2.getId())
                        .assigneeId(mockUser.getUserId())
                        .build();

        org.springframework.data.domain.Page<com.cityfix.backend.model.entity.Ticket> page =
                new org.springframework.data.domain.PageImpl<>(List.of(mockTicket));

        when(ticketStepRepository.findDistinctTicketsByAssigneeUserIdAndStatusIn(eq(mockUser.getUserId()), any(), any())).thenReturn(page);
        when(ticketMapper.toTicketResponse(mockTicket)).thenReturn(mockResponse);
        when(ticketStepMapper.toTicketStepResponseList(List.of(myStep1, myStep2))).thenReturn(List.of(stepRes1, stepRes2));

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getAssignedTicketsForStaff(null, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSteps()).hasSize(2);
        assertThat(result.getContent().get(0).getSteps().get(0).getId()).isEqualTo(myStep1.getId());
        assertThat(result.getContent().get(0).getSteps().get(1).getId()).isEqualTo(myStep2.getId());
    }

    @Test
    @DisplayName("getAssignedTicketsForStaff_enforcesUnsortedPageable")
    void getAssignedTicketsForStaff_enforcesUnsortedPageable() {
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(UUID.randomUUID());
        dept.setName("Phòng Giao thông");
        mockUser.setDepartment(dept);

        org.springframework.data.domain.Page<com.cityfix.backend.model.entity.Ticket> page =
                new org.springframework.data.domain.PageImpl<>(List.of(mockTicket));

        when(ticketStepRepository.findDistinctTicketsByAssigneeUserIdAndStatusIn(any(), any(), any())).thenReturn(page);
        when(ticketMapper.toTicketResponse(mockTicket)).thenReturn(mockResponse);

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

        ticketService.getAssignedTicketsForStaff(
                null,
                org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        );

        verify(ticketStepRepository).findDistinctTicketsByAssigneeUserIdAndStatusIn(
                eq(mockUser.getUserId()),
                eq(List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS, AssignmentStatus.RESOLVED)),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("getAssignedTicketsForStaff_userWithoutDepartment_throwsUSER_NOT_IN_DEPARTMENT")
    void getAssignedTicketsForStaff_userWithoutDepartment_throwsUSER_NOT_IN_DEPARTMENT() {
        mockUser.setDepartment(null);

        assertThatThrownBy(() -> ticketService.getAssignedTicketsForStaff(null, org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_IN_DEPARTMENT);
    }

    @Test
    @DisplayName("assignTicket_success")
    void assignTicket_success() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);

        UUID assigneeId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(UUID.randomUUID());

        User assignee = new User();
        assignee.setUserId(assigneeId);
        assignee.setDepartment(dept);

        com.cityfix.backend.model.entity.TicketStep step = new com.cityfix.backend.model.entity.TicketStep();
        step.setId(UUID.randomUUID());
        step.setAssignee(assignee);
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);
        mockTicket.setSteps(new ArrayList<>(List.of(step)));

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(eq(mockTicket.getTicketId()), any()))
                .thenReturn(List.of(step));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));

        TicketResponse mockResponse = TicketResponse.builder().build();
        when(ticketMapper.toTicketResponse(any())).thenReturn(mockResponse);

        com.cityfix.backend.model.dto.request.AssignTicketRequest req = new com.cityfix.backend.model.dto.request.AssignTicketRequest(step.getId(), assigneeId);

        TicketResponse res = ticketService.assignTicket(mockTicket.getTicketId(), req);

        assertThat(res).isNotNull();
        verify(ticketStepRepository).save(step);
        verify(ticketRepository).save(mockTicket);
        verify(ticketHistoryRepository).save(any());
        assertThat(step.getStatus()).isEqualTo(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);
        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    @DisplayName("forwardTicket_success")
    void forwardTicket_success() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);

        UUID suggestedDeptId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Department suggestedDept = new com.cityfix.backend.model.entity.Department();
        suggestedDept.setDepartmentId(suggestedDeptId);

        SubCategory subCat = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .name("Đèn đường")
                .build();
        TicketSubCategory tsc = TicketSubCategory.builder()
                .ticket(mockTicket)
                .subCategory(subCat)
                .build();
        mockTicket.setSubCategories(new ArrayList<>(List.of(tsc)));

        Step mockStepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(subCat).stepName("Sửa").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCat.getSubCategoryId())).thenReturn(List.of(mockStepEntity));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(suggestedDeptId)).thenReturn(Optional.of(repStaff));

        com.cityfix.backend.model.entity.TicketStep step = new com.cityfix.backend.model.entity.TicketStep();
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(departmentRepository.findById(suggestedDeptId)).thenReturn(Optional.of(suggestedDept));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(eq(mockTicket.getTicketId()), any()))
                .thenReturn(List.of(step));

        TicketResponse mockResponse = TicketResponse.builder().build();
        when(ticketMapper.toTicketResponse(any())).thenReturn(mockResponse);

        com.cityfix.backend.model.dto.request.RejectTicketRequest req = new com.cityfix.backend.model.dto.request.RejectTicketRequest(null, suggestedDeptId, "Reason");

        TicketResponse res = ticketService.forwardTicket(mockTicket.getTicketId(), req);

        assertThat(res).isNotNull();
        verify(ticketHistoryRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("assignTicket_resolvedTicket_throwsTICKET_INVALID_STATUS")
    void assignTicket_resolvedTicket_throwsTICKET_INVALID_STATUS() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);
        mockTicket.setStatus(TicketStatus.RESOLVED);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        com.cityfix.backend.model.dto.request.AssignTicketRequest req = new com.cityfix.backend.model.dto.request.AssignTicketRequest(UUID.randomUUID());

        assertThatThrownBy(() -> ticketService.assignTicket(mockTicket.getTicketId(), req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_INVALID_STATUS);
    }

    @Test
    @DisplayName("forwardTicket_withoutDepartmentOrCategory_throwsDEPARTMENT_NOT_FOUND")
    void forwardTicket_withoutDepartmentOrCategory_throwsDEPARTMENT_NOT_FOUND() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        com.cityfix.backend.model.dto.request.RejectTicketRequest req = new com.cityfix.backend.model.dto.request.RejectTicketRequest(null, null, "Reason");

        assertThatThrownBy(() -> ticketService.forwardTicket(mockTicket.getTicketId(), req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("forwardTicket_userNotAdmin_throwsFORBIDDEN")
    void forwardTicket_userNotAdmin_throwsFORBIDDEN() {
        Role residentRole = new Role(UUID.randomUUID(), "RESIDENT");
        mockUser.setRole(residentRole);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        com.cityfix.backend.model.dto.request.RejectTicketRequest req = new com.cityfix.backend.model.dto.request.RejectTicketRequest(null, null, "Reason");

        assertThatThrownBy(() -> ticketService.forwardTicket(mockTicket.getTicketId(), req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("forwardTicket_withSuggestedCategory_updatesCategoryAndReroutes")
    void forwardTicket_withSuggestedCategory_updatesCategoryAndReroutes() {
        Role adminRole = new Role(UUID.randomUUID(), "ADMIN");
        mockUser.setRole(adminRole);

        UUID suggestedCategoryId = UUID.randomUUID();
        Category suggestedCategory = new Category();
        suggestedCategory.setCategoryId(suggestedCategoryId);

        UUID suggestedDeptId = UUID.randomUUID();
        com.cityfix.backend.model.entity.Department suggestedDept = new com.cityfix.backend.model.entity.Department();
        suggestedDept.setDepartmentId(suggestedDeptId);

        SubCategory subCat = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .name("Đèn đường")
                .build();
        TicketSubCategory tsc = TicketSubCategory.builder()
                .ticket(mockTicket)
                .subCategory(subCat)
                .build();
        mockTicket.setSubCategories(new ArrayList<>(List.of(tsc)));

        Step mockStepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(subCat).stepName("Sửa").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCat.getSubCategoryId())).thenReturn(List.of(mockStepEntity));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(suggestedDeptId)).thenReturn(Optional.of(repStaff));

        com.cityfix.backend.model.entity.TicketStep step = new com.cityfix.backend.model.entity.TicketStep();
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(categoryRepository.findById(suggestedCategoryId)).thenReturn(Optional.of(suggestedCategory));
        when(ticketRepository.save(mockTicket)).thenReturn(mockTicket);

        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(suggestedCategoryId))
            .thenReturn(List.of(com.cityfix.backend.model.entity.RoutingRule.builder().department(suggestedDept).build()));

        when(departmentRepository.findById(suggestedDeptId)).thenReturn(Optional.of(suggestedDept));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(eq(mockTicket.getTicketId()), any()))
                .thenReturn(List.of(step));

        TicketResponse mockResponse = TicketResponse.builder().build();
        when(ticketMapper.toTicketResponse(any())).thenReturn(mockResponse);

        com.cityfix.backend.model.dto.request.RejectTicketRequest req = new com.cityfix.backend.model.dto.request.RejectTicketRequest(suggestedCategoryId, null, "Reason");

        TicketResponse res = ticketService.forwardTicket(mockTicket.getTicketId(), req);

        assertThat(res).isNotNull();
        verify(categoryRepository).findById(suggestedCategoryId);
    }

    @Test
    @DisplayName("assignTicket_assigneeDifferentDepartment_throwsINVALID_REQUEST")
    void assignTicket_assigneeDifferentDepartment_throwsINVALID_REQUEST() {
        com.cityfix.backend.model.entity.Department deptA = new com.cityfix.backend.model.entity.Department();
        deptA.setDepartmentId(UUID.randomUUID());

        com.cityfix.backend.model.entity.Department deptB = new com.cityfix.backend.model.entity.Department();
        deptB.setDepartmentId(UUID.randomUUID());

        com.cityfix.backend.model.entity.Role staffRole = new com.cityfix.backend.model.entity.Role(UUID.randomUUID(), "STAFF");

        mockUser.setRole(staffRole);
        mockUser.setDepartment(deptA);

        User currentStepAssignee = new User();
        currentStepAssignee.setUserId(UUID.randomUUID());
        currentStepAssignee.setDepartment(deptA);

        com.cityfix.backend.model.entity.TicketStep step = new com.cityfix.backend.model.entity.TicketStep();
        step.setId(UUID.randomUUID());
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);
        step.setAssignee(currentStepAssignee);

        mockTicket.setStatus(TicketStatus.ASSIGNED);
        mockTicket.setSteps(new ArrayList<>(List.of(step)));

        User targetAssignee = new User();
        targetAssignee.setUserId(UUID.randomUUID());
        targetAssignee.setDepartment(deptB);

        AssignTicketRequest request = AssignTicketRequest.builder()
                .stepId(step.getId())
                .assigneeId(targetAssignee.getUserId())
                .build();

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(eq(mockTicket.getTicketId()), any()))
                .thenReturn(List.of(step));
        when(userRepository.findById(targetAssignee.getUserId())).thenReturn(Optional.of(targetAssignee));

        assertThatThrownBy(() -> ticketService.assignTicket(mockTicket.getTicketId(), request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("updateTicketStepDeadline_manager_success")
    void updateTicketStepDeadline_manager_success() {
        mockUser.setRole(new Role(UUID.randomUUID(), "MANAGER"));
        UUID stepId = UUID.randomUUID();
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);
        TicketStep step = TicketStep.builder().id(stepId).build();

        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepRepository.save(any(TicketStep.class))).thenReturn(step);
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId).deadline(deadline).build()
        );

        com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest request =
                new com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest(deadline);

        com.cityfix.backend.model.dto.response.TicketStepResponse res =
                ticketService.updateTicketStepDeadline(stepId, request);

        assertThat(res).isNotNull();
        assertThat(res.getDeadline()).isEqualTo(deadline);
        verify(ticketStepMapper).updateDeadline(eq(request), eq(step));
        verify(ticketStepRepository).save(step);
    }

    @Test
    @DisplayName("updateTicketStepDeadline_notManager_throwsForbidden")
    void updateTicketStepDeadline_notManager_throwsForbidden() {
        mockUser.setRole(new Role(UUID.randomUUID(), "STAFF"));
        UUID stepId = UUID.randomUUID();
        com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest request =
                new com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest(LocalDateTime.now().plusDays(2));

        assertThatThrownBy(() -> ticketService.updateTicketStepDeadline(stepId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updateTicketStepOrderIndex_manager_success")
    void updateTicketStepOrderIndex_manager_success() {
        mockUser.setRole(new Role(UUID.randomUUID(), "MANAGER"));
        UUID stepId = UUID.randomUUID();
        TicketStep step = TicketStep.builder().id(stepId).build();

        when(ticketStepRepository.findById(stepId)).thenReturn(Optional.of(step));
        when(ticketStepMapper.toTicketStepResponse(any(TicketStep.class))).thenReturn(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId).build()
        );

        com.cityfix.backend.model.dto.request.UpdateTicketStepOrderRequest request =
                new com.cityfix.backend.model.dto.request.UpdateTicketStepOrderRequest(3);

        com.cityfix.backend.model.dto.response.TicketStepResponse res =
                ticketService.updateTicketStepOrderIndex(stepId, request);

        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(stepId);
    }

    @Test
    @DisplayName("updateTicketStepDeadline_pastDeadline_throwsInvalidRequest")
    void updateTicketStepDeadline_pastDeadline_throwsInvalidRequest() {
        mockUser.setRole(new Role(UUID.randomUUID(), "MANAGER"));
        UUID stepId = UUID.randomUUID();
        com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest request =
                new com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlineRequest(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> ticketService.updateTicketStepDeadline(stepId, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("updateTicketStepDeadlines_manager_success")
    void updateTicketStepDeadlines_manager_success() {
        mockUser.setRole(new Role(UUID.randomUUID(), "MANAGER"));
        UUID ticketId = UUID.randomUUID();
        UUID stepId1 = UUID.randomUUID();
        UUID stepId2 = UUID.randomUUID();
        TicketStep step1 = TicketStep.builder().id(stepId1).build();
        TicketStep step2 = TicketStep.builder().id(stepId2).build();
        LocalDateTime deadline1 = LocalDateTime.now().plusDays(2);
        LocalDateTime deadline2 = LocalDateTime.now().plusDays(3);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of(step1, step2));
        when(ticketStepRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketStepMapper.toTicketStepResponseList(anyList())).thenReturn(List.of(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId1).deadline(deadline1).build(),
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId2).deadline(deadline2).build()
        ));

        com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlinesRequest request =
                new com.cityfix.backend.model.dto.request.UpdateTicketStepDeadlinesRequest(List.of(
                        new com.cityfix.backend.model.dto.request.StepDeadlineItem(stepId1, deadline1),
                        new com.cityfix.backend.model.dto.request.StepDeadlineItem(stepId2, deadline2)
                ));

        List<com.cityfix.backend.model.dto.response.TicketStepResponse> res =
                ticketService.updateTicketStepDeadlines(ticketId, request);

        assertThat(res).hasSize(2);
        verify(ticketStepRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("reorderTicketSteps_manager_success")
    void reorderTicketSteps_manager_success() {
        mockUser.setRole(new Role(UUID.randomUUID(), "MANAGER"));
        UUID ticketId = UUID.randomUUID();
        UUID stepId1 = UUID.randomUUID();
        UUID stepId2 = UUID.randomUUID();
        TicketStep step1 = TicketStep.builder().id(stepId1).build();
        TicketStep step2 = TicketStep.builder().id(stepId2).build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ticketStepRepository.findByTicketTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(List.of(step1, step2));
        when(ticketStepMapper.toTicketStepResponseList(anyList())).thenReturn(List.of(
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId1).build(),
                com.cityfix.backend.model.dto.response.TicketStepResponse.builder().id(stepId2).build()
        ));

        com.cityfix.backend.model.dto.request.ReorderTicketStepsRequest request =
                new com.cityfix.backend.model.dto.request.ReorderTicketStepsRequest(List.of(
                        new com.cityfix.backend.model.dto.request.StepOrderItem(stepId1, 1),
                        new com.cityfix.backend.model.dto.request.StepOrderItem(stepId2, 0)
                ));

        List<com.cityfix.backend.model.dto.response.TicketStepResponse> res =
                ticketService.reorderTicketSteps(ticketId, request);

        assertThat(res).hasSize(2);
    }

    // ==========================================
    // 6. Ticket Deadline Calculation Tests
    // ==========================================

    @Test
    @DisplayName("calculateTicketDeadline_withSteps_returnsMaxDeadline")
    void calculateTicketDeadline_withSteps_returnsMaxDeadline() {
        java.time.LocalDateTime created = java.time.LocalDateTime.of(2026, 10, 11, 10, 0);
        java.time.LocalDateTime deadline1 = created.plusHours(24);
        java.time.LocalDateTime deadline2 = created.plusHours(48).plusMinutes(15);

        TicketStep step1 = TicketStep.builder().deadline(deadline1).build();
        TicketStep step2 = TicketStep.builder().deadline(deadline2).build();

        Ticket ticket = Ticket.builder()
                .createdAt(created)
                .steps(List.of(step1, step2))
                .build();

        LocalDateTime ticketDeadline = ticketService.calculateTicketDeadline(ticket);

        assertThat(ticketDeadline).isEqualTo(deadline2);
    }

    @Test
    @DisplayName("calculateTicketDeadline_ignoresCancelledAndTransferredSteps")
    void calculateTicketDeadline_ignoresCancelledAndTransferredSteps() {
        java.time.LocalDateTime created = java.time.LocalDateTime.of(2026, 10, 11, 10, 0);
        java.time.LocalDateTime activeDeadline = created.plusHours(24);
        java.time.LocalDateTime cancelledDeadline = created.plusHours(72);

        TicketStep activeStep = TicketStep.builder()
                .status(AssignmentStatus.ASSIGNED)
                .deadline(activeDeadline)
                .build();
        TicketStep cancelledStep = TicketStep.builder()
                .status(AssignmentStatus.CANCELLED)
                .deadline(cancelledDeadline)
                .build();

        Ticket ticket = Ticket.builder()
                .createdAt(created)
                .steps(List.of(activeStep, cancelledStep))
                .build();

        LocalDateTime ticketDeadline = ticketService.calculateTicketDeadline(ticket);

        assertThat(ticketDeadline).isEqualTo(activeDeadline);
    }

    @Test
    @DisplayName("calculateTicketDeadline_withoutDeadlines_returnsNull")
    void calculateTicketDeadline_withoutDeadlines_returnsNull() {
        TicketStep step1 = TicketStep.builder().deadline(null).build();

        Ticket ticket = Ticket.builder()
                .steps(List.of(step1))
                .build();

        LocalDateTime ticketDeadline = ticketService.calculateTicketDeadline(ticket);

        assertThat(ticketDeadline).isNull();
    }

    @Test
    @DisplayName("calculateTicketDeadline_nullOrEmptySteps_returnsNull")
    void calculateTicketDeadline_nullOrEmptySteps_returnsNull() {
        Ticket ticket = Ticket.builder().steps(Collections.emptyList()).build();

        LocalDateTime ticketDeadline = ticketService.calculateTicketDeadline(ticket);

        assertThat(ticketDeadline).isNull();
    }

    @Test
    @DisplayName("getMyUpvotedTickets_success_returnsPagedTicketResponses")
    void getMyUpvotedTickets_success_returnsPagedTicketResponses() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        Ticket ticket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Hố ga mất nắp")
                .status(TicketStatus.IN_PROGRESS)
                .build();
        TicketResponse expectedResponse = TicketResponse.builder()
                .id(ticket.getTicketId())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .build();

        org.springframework.data.domain.Page<Ticket> ticketPage =
                new org.springframework.data.domain.PageImpl<>(List.of(ticket), pageable, 1);

        when(ticketRepository.findUpvotedTicketsByUserId(eq(mockUser.getUserId()), eq(pageable)))
                .thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(expectedResponse);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getMyUpvotedTickets(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ticket.getTicketId());
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Hố ga mất nắp");

        verify(ticketRepository).findUpvotedTicketsByUserId(mockUser.getUserId(), pageable);
        verify(ticketMapper).toTicketResponse(ticket);
    }

    @Test
    @DisplayName("getMyUpvotedTickets_emptyResult_returnsEmptyPage")
    void getMyUpvotedTickets_emptyResult_returnsEmptyPage() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Ticket> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, 0);

        when(ticketRepository.findUpvotedTicketsByUserId(eq(mockUser.getUserId()), eq(pageable)))
                .thenReturn(emptyPage);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getMyUpvotedTickets(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();

        verify(ticketRepository).findUpvotedTicketsByUserId(mockUser.getUserId(), pageable);
        verify(ticketMapper, never()).toTicketResponse(any());
    }

    @Test
    @DisplayName("getMyUpvotedTickets_unauthenticated_throwsUnauthenticated")
    void getMyUpvotedTickets_unauthenticated_throwsUnauthenticated() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> ticketService.getMyUpvotedTickets(pageable))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verify(ticketRepository, never()).findUpvotedTicketsByUserId(any(), any());
    }

    @Test
    @DisplayName("getInProgressTickets_success_returnsPagedInProgressTicketsRegardlessOfOwner")
    void getInProgressTickets_success_returnsPagedInProgressTicketsRegardlessOfOwner() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        Ticket ticket1 = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố đèn đường hỏng")
                .status(TicketStatus.IN_PROGRESS)
                .build();
        Ticket ticket2 = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố cây đổ")
                .status(TicketStatus.IN_PROGRESS)
                .build();

        TicketResponse resp1 = TicketResponse.builder()
                .id(ticket1.getTicketId())
                .description(ticket1.getDescription())
                .status(TicketStatus.IN_PROGRESS)
                .build();
        TicketResponse resp2 = TicketResponse.builder()
                .id(ticket2.getTicketId())
                .description(ticket2.getDescription())
                .status(TicketStatus.IN_PROGRESS)
                .build();

        org.springframework.data.domain.Page<Ticket> ticketPage =
                new org.springframework.data.domain.PageImpl<>(List.of(ticket1, ticket2), pageable, 2);

        when(ticketRepository.findByStatus(eq(TicketStatus.IN_PROGRESS), eq(pageable)))
                .thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket1)).thenReturn(resp1);
        when(ticketMapper.toTicketResponse(ticket2)).thenReturn(resp2);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getInProgressTickets(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ticket1.getTicketId());
        assertThat(result.getContent().get(1).getId()).isEqualTo(ticket2.getTicketId());

        verify(ticketRepository).findByStatus(TicketStatus.IN_PROGRESS, pageable);
        verify(ticketMapper).toTicketResponse(ticket1);
        verify(ticketMapper).toTicketResponse(ticket2);
    }

    @Test
    @DisplayName("getInProgressTickets_emptyResult_returnsEmptyPage")
    void getInProgressTickets_emptyResult_returnsEmptyPage() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Ticket> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, 0);

        when(ticketRepository.findByStatus(eq(TicketStatus.IN_PROGRESS), eq(pageable)))
                .thenReturn(emptyPage);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getInProgressTickets(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();

        verify(ticketRepository).findByStatus(TicketStatus.IN_PROGRESS, pageable);
        verify(ticketMapper, never()).toTicketResponse(any());
    }

    @Test
    @DisplayName("getInProgressTickets_boundaryPageSizeOne_returnsSingleElementPage")
    void getInProgressTickets_boundaryPageSizeOne_returnsSingleElementPage() {
        org.springframework.data.domain.Pageable boundaryPageable = org.springframework.data.domain.PageRequest.of(0, 1);
        Ticket ticket1 = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố sụt lún mặt đường")
                .status(TicketStatus.IN_PROGRESS)
                .build();
        TicketResponse resp1 = TicketResponse.builder()
                .id(ticket1.getTicketId())
                .description(ticket1.getDescription())
                .status(TicketStatus.IN_PROGRESS)
                .build();

        org.springframework.data.domain.Page<Ticket> ticketPage =
                new org.springframework.data.domain.PageImpl<>(List.of(ticket1), boundaryPageable, 5);

        when(ticketRepository.findByStatus(eq(TicketStatus.IN_PROGRESS), eq(boundaryPageable)))
                .thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket1)).thenReturn(resp1);

        org.springframework.data.domain.Page<TicketResponse> result = ticketService.getInProgressTickets(boundaryPageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(5);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ticket1.getTicketId());
    }

    @Test
    @DisplayName("getTicketHistory_existingTicketWithMultipleHistories_returnsOrderedList")
    void getTicketHistory_existingTicketWithMultipleHistories_returnsOrderedList() {
        UUID ticketId = UUID.randomUUID();
        TicketHistory h1 = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .actionNote("Chuyển trạng thái sang IN_PROGRESS")
                .build();
        TicketHistory h2 = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .actionNote("Khởi tạo sự cố")
                .build();

        com.cityfix.backend.model.dto.response.TicketHistoryResponse r1 =
                com.cityfix.backend.model.dto.response.TicketHistoryResponse.builder()
                        .historyId(h1.getHistoryId())
                        .ticketId(ticketId)
                        .actionNote(h1.getActionNote())
                        .build();
        com.cityfix.backend.model.dto.response.TicketHistoryResponse r2 =
                com.cityfix.backend.model.dto.response.TicketHistoryResponse.builder()
                        .historyId(h2.getHistoryId())
                        .ticketId(ticketId)
                        .actionNote(h2.getActionNote())
                        .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ticketHistoryRepository.findByTicketTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of(h1, h2));
        when(ticketHistoryMapper.toTicketHistoryResponseList(List.of(h1, h2))).thenReturn(List.of(r1, r2));

        List<com.cityfix.backend.model.dto.response.TicketHistoryResponse> result = ticketService.getTicketHistory(ticketId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getHistoryId()).isEqualTo(h1.getHistoryId());
        assertThat(result.get(1).getHistoryId()).isEqualTo(h2.getHistoryId());
        verify(ticketRepository).existsById(ticketId);
        verify(ticketHistoryRepository).findByTicketTicketIdOrderByCreatedAtDesc(ticketId);
    }

    @Test
    @DisplayName("getTicketHistory_existingTicketWithSingleCreationEntry_returnsSingleItemList")
    void getTicketHistory_existingTicketWithSingleCreationEntry_returnsSingleItemList() {
        UUID ticketId = UUID.randomUUID();
        TicketHistory h1 = TicketHistory.builder()
                .historyId(UUID.randomUUID())
                .actionNote("Khởi tạo sự cố mới")
                .build();
        com.cityfix.backend.model.dto.response.TicketHistoryResponse r1 =
                com.cityfix.backend.model.dto.response.TicketHistoryResponse.builder()
                        .historyId(h1.getHistoryId())
                        .ticketId(ticketId)
                        .actionNote(h1.getActionNote())
                        .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ticketHistoryRepository.findByTicketTicketIdOrderByCreatedAtDesc(ticketId)).thenReturn(List.of(h1));
        when(ticketHistoryMapper.toTicketHistoryResponseList(List.of(h1))).thenReturn(List.of(r1));

        List<com.cityfix.backend.model.dto.response.TicketHistoryResponse> result = ticketService.getTicketHistory(ticketId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHistoryId()).isEqualTo(h1.getHistoryId());
    }

    @Test
    @DisplayName("getTicketHistory_ticketNotFound_throwsTicketNotFound")
    void getTicketHistory_ticketNotFound_throwsTicketNotFound() {
        UUID unknownTicketId = UUID.randomUUID();
        when(ticketRepository.existsById(unknownTicketId)).thenReturn(false);

        assertThatThrownBy(() -> ticketService.getTicketHistory(unknownTicketId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);

        verify(ticketHistoryRepository, never()).findByTicketTicketIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("upvoteTicket_activeTicketNotYetUpvoted_persistsVote")
    void upvoteTicket_activeTicketNotYetUpvoted_persistsVote() {
        UUID ticketId = mockTicket.getTicketId();
        User ticketCreator = User.builder().userId(UUID.randomUUID()).email("creator@cityfix.com").build();
        mockTicket.setUser(ticketCreator);
        mockTicket.setStatus(TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, mockUser.getUserId())).thenReturn(false);

        ticketService.upvoteTicket(ticketId);

        verify(ticketVoteRepository).save(argThat(v -> v.getTicket().equals(mockTicket) && v.getUser().equals(mockUser)));
    }

    @Test
    @DisplayName("upvoteTicket_creatorUpvotesOwnTicket_throwsTicketCreatorCannotUpvote")
    void upvoteTicket_creatorUpvotesOwnTicket_throwsTicketCreatorCannotUpvote() {
        UUID ticketId = mockTicket.getTicketId();
        mockTicket.setStatus(TicketStatus.INITIATED);
        mockTicket.setUser(mockUser);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.upvoteTicket(ticketId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_CREATOR_CANNOT_UPVOTE);

        verify(ticketVoteRepository, never()).existsByTicket_TicketIdAndUser_UserId(any(), any());
        verify(ticketVoteRepository, never()).save(any(TicketVote.class));
    }

    @Test
    @DisplayName("upvoteTicket_alreadyUpvoted_throwsTicketAlreadyUpvoted")
    void upvoteTicket_alreadyUpvoted_throwsTicketAlreadyUpvoted() {
        UUID ticketId = mockTicket.getTicketId();
        User ticketCreator = User.builder().userId(UUID.randomUUID()).email("creator@cityfix.com").build();
        mockTicket.setUser(ticketCreator);
        mockTicket.setStatus(TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketVoteRepository.existsByTicket_TicketIdAndUser_UserId(ticketId, mockUser.getUserId())).thenReturn(true);

        assertThatThrownBy(() -> ticketService.upvoteTicket(ticketId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_ALREADY_UPVOTED);

        verify(ticketVoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("upvoteTicket_terminalStatus_throwsTicketCannotUpvote")
    void upvoteTicket_terminalStatus_throwsTicketCannotUpvote() {
        UUID ticketId = mockTicket.getTicketId();
        User ticketCreator = User.builder().userId(UUID.randomUUID()).email("creator@cityfix.com").build();
        mockTicket.setUser(ticketCreator);
        for (TicketStatus terminalStatus : List.of(TicketStatus.RESOLVED, TicketStatus.COMPLETED, TicketStatus.CANCELLED)) {
            mockTicket.setStatus(terminalStatus);
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));

            assertThatThrownBy(() -> ticketService.upvoteTicket(ticketId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TICKET_CANNOT_UPVOTE);
        }
        verify(ticketVoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("upvoteTicket_ticketNotFound_throwsTicketNotFound")
    void upvoteTicket_ticketNotFound_throwsTicketNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.upvoteTicket(unknownId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("getTicketUpvotes_ticketExistsWithVotes_returnsCountAndUpvoters")
    void getTicketUpvotes_ticketExistsWithVotes_returnsCountAndUpvoters() {
        UUID ticketId = mockTicket.getTicketId();
        LocalDateTime firstVoteTime = LocalDateTime.of(2026, 11, 26, 10, 0);
        LocalDateTime secondVoteTime = LocalDateTime.of(2026, 11, 26, 12, 30);

        User upvoter1 = User.builder()
                .userId(UUID.randomUUID())
                .email("u1@cityfix.com")
                .fullName("Nguyễn Văn A")
                .phoneNumber("0901111111")
                .build();
        User upvoter2 = User.builder()
                .userId(UUID.randomUUID())
                .email("u2@cityfix.com")
                .fullName("Trần Thị B")
                .phoneNumber("0902222222")
                .build();

        TicketVote vote2 = TicketVote.builder()
                .voteId(UUID.randomUUID())
                .ticket(mockTicket)
                .user(upvoter2)
                .createdAt(secondVoteTime)
                .build();
        TicketVote vote1 = TicketVote.builder()
                .voteId(UUID.randomUUID())
                .ticket(mockTicket)
                .user(upvoter1)
                .createdAt(firstVoteTime)
                .build();

        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ticketVoteRepository.findByTicketIdWithUserOrderByCreatedAtDesc(ticketId))
                .thenReturn(List.of(vote2, vote1));

        TicketUpvotesResponse result = ticketService.getTicketUpvotes(ticketId);

        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(ticketId);
        assertThat(result.getUpvoteCount()).isEqualTo(2L);
        assertThat(result.getUpvoters()).hasSize(2);
        assertThat(result.getUpvoters().get(0))
                .extracting(TicketUpvoterResponse::getUserId, TicketUpvoterResponse::getFullName, TicketUpvoterResponse::getUpvotedAt)
                .containsExactly(upvoter2.getUserId(), "Trần Thị B", secondVoteTime);
        assertThat(result.getUpvoters().get(1))
                .extracting(TicketUpvoterResponse::getUserId, TicketUpvoterResponse::getFullName, TicketUpvoterResponse::getUpvotedAt)
                .containsExactly(upvoter1.getUserId(), "Nguyễn Văn A", firstVoteTime);
    }

    @Test
    @DisplayName("getTicketUpvotes_ticketExistsWithoutVotes_returnsZeroAndEmptyList")
    void getTicketUpvotes_ticketExistsWithoutVotes_returnsZeroAndEmptyList() {
        UUID ticketId = mockTicket.getTicketId();
        when(ticketRepository.existsById(ticketId)).thenReturn(true);
        when(ticketVoteRepository.findByTicketIdWithUserOrderByCreatedAtDesc(ticketId))
                .thenReturn(Collections.emptyList());

        TicketUpvotesResponse result = ticketService.getTicketUpvotes(ticketId);

        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(ticketId);
        assertThat(result.getUpvoteCount()).isZero();
        assertThat(result.getUpvoters()).isEmpty();
    }

    @Test
    @DisplayName("getTicketUpvotes_ticketNotFound_throwsTicketNotFound")
    void getTicketUpvotes_ticketNotFound_throwsTicketNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.existsById(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> ticketService.getTicketUpvotes(unknownId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);

        verify(ticketVoteRepository, never()).findByTicketIdWithUserOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("cancelTicket_creatorCancelsInitiatedTicket_cancelsTicketAndSteps")
    void cancelTicket_creatorCancelsInitiatedTicket_cancelsTicketAndSteps() {
        UUID ticketId = mockTicket.getTicketId();
        mockTicket.setStatus(TicketStatus.INITIATED);
        mockTicket.setUser(mockUser);

        TicketStep pendingStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketId(ticketId))
                .thenReturn(List.of(pendingStep));
        when(ticketRepository.save(mockTicket)).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(mockTicket)).thenReturn(
                TicketResponse.builder().id(ticketId).status(TicketStatus.CANCELLED).build()
        );

        TicketResponse result = ticketService.cancelTicket(ticketId);

        assertThat(result).isNotNull();
        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(pendingStep.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        verify(ticketHistoryRepository).save(any(TicketHistory.class));
        verify(ticketRepository).save(mockTicket);
    }

    @Test
    @DisplayName("cancelTicket_nonInitiatedStatus_throwsTicketInvalidStatus")
    void cancelTicket_nonInitiatedStatus_throwsTicketInvalidStatus() {
        UUID ticketId = mockTicket.getTicketId();
        mockTicket.setStatus(TicketStatus.IN_PROGRESS);
        mockTicket.setUser(mockUser);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.cancelTicket(ticketId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_INVALID_STATUS);
    }

    @Test
    @DisplayName("cancelTicket_notCreator_throwsForbidden")
    void cancelTicket_notCreator_throwsForbidden() {
        UUID ticketId = mockTicket.getTicketId();
        User otherUser = User.builder().userId(UUID.randomUUID()).build();
        mockTicket.setStatus(TicketStatus.INITIATED);
        mockTicket.setUser(otherUser);
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.cancelTicket(ticketId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("cancelTicket_ticketNotFound_throwsTicketNotFound")
    void cancelTicket_ticketNotFound_throwsTicketNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.cancelTicket(unknownId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("getAssignedTicketsForStaff_withSpecificStatusFilter_passesStatusToRepository")
    void getAssignedTicketsForStaff_withSpecificStatusFilter_passesStatusToRepository() {
        Department dept = Department.builder().departmentId(UUID.randomUUID()).name("Phòng Giao thông").build();
        mockUser.setDepartment(dept);

        TicketStep staffStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .assignee(mockUser)
                .status(AssignmentStatus.IN_PROGRESS)
                .build();
        mockTicket.setSteps(List.of(staffStep));

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(ticketStepRepository.findDistinctTicketsByAssigneeUserIdAndStatusIn(
                eq(mockUser.getUserId()),
                eq(List.of(AssignmentStatus.IN_PROGRESS)),
                any()
        )).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(mockTicket), pageable, 1));
        when(ticketMapper.toTicketResponse(mockTicket)).thenReturn(TicketResponse.builder().id(mockTicket.getTicketId()).build());
        when(ticketStepMapper.toTicketStepResponseList(List.of(staffStep))).thenReturn(List.of());

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getAssignedTicketsForStaff(AssignmentStatus.IN_PROGRESS, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(ticketStepRepository).findDistinctTicketsByAssigneeUserIdAndStatusIn(
                eq(mockUser.getUserId()),
                eq(List.of(AssignmentStatus.IN_PROGRESS)),
                any()
        );
    }

    @Test
    @DisplayName("getAssignedTicketsForStaff_whenNoAssignedTickets_returnsEmptyPage")
    void getAssignedTicketsForStaff_whenNoAssignedTickets_returnsEmptyPage() {
        Department dept = Department.builder().departmentId(UUID.randomUUID()).name("Phòng Giao thông").build();
        mockUser.setDepartment(dept);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(ticketStepRepository.findDistinctTicketsByAssigneeUserIdAndStatusIn(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getAssignedTicketsForStaff(AssignmentStatus.IN_PROGRESS, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("assignTicket_ticketNotFound_throwsTicketNotFound")
    void assignTicket_ticketNotFound_throwsTicketNotFound() {
        UUID unknownTicketId = UUID.randomUUID();
        when(ticketRepository.findById(unknownTicketId)).thenReturn(Optional.empty());

        AssignTicketRequest req = new AssignTicketRequest(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> ticketService.assignTicket(unknownTicketId, req))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("assignTicket_assigneeNotFound_throwsAccountNotFound")
    void assignTicket_assigneeNotFound_throwsAccountNotFound() {
        mockUser.setRole(new Role(UUID.randomUUID(), "ADMIN"));
        UUID unknownAssigneeId = UUID.randomUUID();
        TicketStep step = TicketStep.builder()
                .id(UUID.randomUUID())
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByCreatedAtAsc(eq(mockTicket.getTicketId()), any()))
                .thenReturn(List.of(step));
        when(userRepository.findById(unknownAssigneeId)).thenReturn(Optional.empty());

        AssignTicketRequest req = new AssignTicketRequest(step.getId(), unknownAssigneeId);

        assertThatThrownBy(() -> ticketService.assignTicket(mockTicket.getTicketId(), req))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("forwardTicket_ticketNotFound_throwsTicketNotFound")
    void forwardTicket_ticketNotFound_throwsTicketNotFound() {
        UUID unknownTicketId = UUID.randomUUID();
        when(ticketRepository.findById(unknownTicketId)).thenReturn(Optional.empty());

        com.cityfix.backend.model.dto.request.RejectTicketRequest req =
                new com.cityfix.backend.model.dto.request.RejectTicketRequest(null, UUID.randomUUID(), "Sai đơn vị");

        assertThatThrownBy(() -> ticketService.forwardTicket(unknownTicketId, req))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("rejectAssignedTicket_assignedStaffInTicketDepartment_rejectsTicketAndCancelsSteps")
    void rejectAssignedTicket_assignedStaffInTicketDepartment_rejectsTicketAndCancelsSteps() {
        Department dept = Department.builder().departmentId(UUID.randomUUID()).name("Phòng Hạ tầng").build();
        mockUser.setRole(new Role(UUID.randomUUID(), "STAFF"));
        mockUser.setDepartment(dept);

        TicketStep assignedStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .assignee(mockUser)
                .status(AssignmentStatus.ASSIGNED)
                .build();
        mockTicket.setStatus(TicketStatus.ASSIGNED);
        mockTicket.setSteps(List.of(assignedStep));

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketId(mockTicket.getTicketId()))
                .thenReturn(List.of(assignedStep));
        when(ticketRepository.save(mockTicket)).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(mockTicket)).thenReturn(
                TicketResponse.builder().id(mockTicket.getTicketId()).status(TicketStatus.REJECTED).reviewNote("Không thuộc thẩm quyền").build()
        );

        TicketResponse result = ticketService.rejectAssignedTicket(mockTicket.getTicketId(), "Không thuộc thẩm quyền");

        assertThat(result).isNotNull();
        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.REJECTED);
        assertThat(mockTicket.getReviewNote()).isEqualTo("Không thuộc thẩm quyền");
        assertThat(assignedStep.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        verify(ticketHistoryRepository).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("rejectAssignedTicket_userNotBelongingToAssignedDepartment_throwsForbidden")
    void rejectAssignedTicket_userNotBelongingToAssignedDepartment_throwsForbidden() {
        Department deptA = Department.builder().departmentId(UUID.randomUUID()).name("Phòng A").build();
        Department deptB = Department.builder().departmentId(UUID.randomUUID()).name("Phòng B").build();
        mockUser.setRole(new Role(UUID.randomUUID(), "STAFF"));
        mockUser.setDepartment(deptB);

        User assigneeInDeptA = User.builder().userId(UUID.randomUUID()).department(deptA).build();
        TicketStep assignedStep = TicketStep.builder()
                .id(UUID.randomUUID())
                .ticket(mockTicket)
                .assignee(assigneeInDeptA)
                .status(AssignmentStatus.ASSIGNED)
                .build();
        mockTicket.setStatus(TicketStatus.ASSIGNED);
        mockTicket.setSteps(List.of(assignedStep));

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.rejectAssignedTicket(mockTicket.getTicketId(), "Từ chối"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("rejectAssignedTicket_ticketNotInAssignedStatus_throwsTicketInvalidStatus")
    void rejectAssignedTicket_ticketNotInAssignedStatus_throwsTicketInvalidStatus() {
        mockUser.setRole(new Role(UUID.randomUUID(), "ADMIN"));
        mockTicket.setStatus(TicketStatus.RESOLVED);
        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.rejectAssignedTicket(mockTicket.getTicketId(), "Từ chối"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_INVALID_STATUS);
    }

    @Test
    @DisplayName("rejectAssignedTicket_ticketNotFound_throwsTicketNotFound")
    void rejectAssignedTicket_ticketNotFound_throwsTicketNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(ticketRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.rejectAssignedTicket(unknownId, "Từ chối"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_success_normalizesWardAndReturnsPagedTickets")
    void getLifeThreateningTicketsInSameWard_success_normalizesWardAndReturnsPagedTickets() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard("Phường Bến Nghé");
        mockUser.setProvince("TP. Hồ Chí Minh");

        Ticket ticket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Dây điện cao thế đứt rơi xuống đường")
                .address("123 Nguyễn Huệ, P. Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .isLifeThreatening(true)
                .status(TicketStatus.IN_PROGRESS)
                .build();

        TicketResponse expectedResponse = TicketResponse.builder()
                .id(ticket.getTicketId())
                .description(ticket.getDescription())
                .address(ticket.getAddress())
                .status(TicketStatus.IN_PROGRESS)
                .build();

        List<TicketStatus> expectedStatuses = List.of(
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED
        );

        org.springframework.data.domain.Page<Ticket> ticketPage =
                new org.springframework.data.domain.PageImpl<>(List.of(ticket), pageable, 1);

        when(ticketRepository.findLifeThreateningTicketsByWard(
                eq("Phường Bến Nghé"),
                eq("Bến Nghé"),
                eq("Hồ Chí Minh"),
                eq(expectedStatuses),
                eq(pageable)
        )).thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(expectedResponse);

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getLifeThreateningTicketsInSameWard(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ticket.getTicketId());

        verify(ticketRepository).findLifeThreateningTicketsByWard(
                "Phường Bến Nghé", "Bến Nghé", "Hồ Chí Minh", expectedStatuses, pageable
        );
        verify(ticketMapper).toTicketResponse(ticket);
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_nullOrBlankUserWard_returnsEmptyPageWithoutQuerying")
    void getLifeThreateningTicketsInSameWard_nullOrBlankUserWard_returnsEmptyPageWithoutQuerying() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard("   ");

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getLifeThreateningTicketsInSameWard(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(ticketRepository, never()).findLifeThreateningTicketsByWard(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_nullProvince_passesEmptyStringProvince")
    void getLifeThreateningTicketsInSameWard_nullProvince_passesEmptyStringProvince() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard("P. Đa Kao");
        mockUser.setProvince(null);

        List<TicketStatus> expectedStatuses = List.of(
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED
        );

        org.springframework.data.domain.Page<Ticket> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, 0);

        when(ticketRepository.findLifeThreateningTicketsByWard(
                eq("P. Đa Kao"),
                eq("Đa Kao"),
                eq(""),
                eq(expectedStatuses),
                eq(pageable)
        )).thenReturn(emptyPage);

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getLifeThreateningTicketsInSameWard(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(ticketRepository).findLifeThreateningTicketsByWard(
                "P. Đa Kao", "Đa Kao", "", expectedStatuses, pageable
        );
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_unauthenticated_throwsUnauthenticated")
    void getLifeThreateningTicketsInSameWard_unauthenticated_throwsUnauthenticated() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> ticketService.getLifeThreateningTicketsInSameWard(pageable))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verify(ticketRepository, never()).findLifeThreateningTicketsByWard(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_accountNotFound_throwsAccountNotFound")
    void getLifeThreateningTicketsInSameWard_accountNotFound_throwsAccountNotFound() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(authentication.getPrincipal()).thenReturn("unknown@cityfix.com");
        when(userRepository.findByEmail("unknown@cityfix.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getLifeThreateningTicketsInSameWard(pageable))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(ticketRepository, never()).findLifeThreateningTicketsByWard(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_wardOnlyContainsPrefix_returnsEmptyPageWithoutQuerying")
    void getLifeThreateningTicketsInSameWard_wardOnlyContainsPrefix_returnsEmptyPageWithoutQuerying() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard("Phường   ");

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getLifeThreateningTicketsInSameWard(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(ticketRepository, never()).findLifeThreateningTicketsByWard(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_nullWard_returnsEmptyPageWithoutQuerying")
    void getLifeThreateningTicketsInSameWard_nullWard_returnsEmptyPageWithoutQuerying() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard(null);

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getLifeThreateningTicketsInSameWard(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(ticketRepository, never()).findLifeThreateningTicketsByWard(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_numericWard_retainsPhuongPrefixToPreventFalsePositives")
    void getLifeThreateningTicketsInSameWard_numericWard_retainsPhuongPrefixToPreventFalsePositives() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard("P. 1");
        mockUser.setProvince("TP. Hồ Chí Minh");

        Ticket ticket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Sập giàn giáo công trình")
                .address("15 Lê Thánh Tôn, Phường 1, Quận 3, TP. Hồ Chí Minh")
                .isLifeThreatening(true)
                .status(TicketStatus.ASSIGNED)
                .build();

        TicketResponse expectedResponse = TicketResponse.builder()
                .id(ticket.getTicketId())
                .description(ticket.getDescription())
                .address(ticket.getAddress())
                .status(TicketStatus.ASSIGNED)
                .build();

        List<TicketStatus> expectedStatuses = List.of(
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED
        );

        org.springframework.data.domain.Page<Ticket> ticketPage =
                new org.springframework.data.domain.PageImpl<>(List.of(ticket), pageable, 1);

        when(ticketRepository.findLifeThreateningTicketsByWard(
                eq("P. 1"),
                eq("Phường 1"),
                eq("Hồ Chí Minh"),
                eq(expectedStatuses),
                eq(pageable)
        )).thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(expectedResponse);

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getLifeThreateningTicketsInSameWard(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(ticket.getTicketId());

        verify(ticketRepository).findLifeThreateningTicketsByWard(
                "P. 1", "Phường 1", "Hồ Chí Minh", expectedStatuses, pageable
        );
        verify(ticketMapper).toTicketResponse(ticket);
    }

    @Test
    @DisplayName("getLifeThreateningTicketsInSameWard_provinceVariants_normalizesToCoreKeywordHoChiMinh")
    void getLifeThreateningTicketsInSameWard_provinceVariants_normalizesToCoreKeywordHoChiMinh() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        mockUser.setWard("Phường Bến Nghé");

        Ticket ticket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description("Cháy trạm biến áp")
                .address("10 Đồng Khởi, Phường Bến Nghé, TP. Hồ Chí Minh")
                .isLifeThreatening(true)
                .status(TicketStatus.IN_PROGRESS)
                .build();

        TicketResponse expectedResponse = TicketResponse.builder()
                .id(ticket.getTicketId())
                .description(ticket.getDescription())
                .address(ticket.getAddress())
                .status(TicketStatus.IN_PROGRESS)
                .build();

        List<TicketStatus> expectedStatuses = List.of(
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED
        );

        org.springframework.data.domain.Page<Ticket> ticketPage =
                new org.springframework.data.domain.PageImpl<>(List.of(ticket), pageable, 1);

        when(ticketRepository.findLifeThreateningTicketsByWard(
                eq("Phường Bến Nghé"),
                eq("Bến Nghé"),
                eq("Hồ Chí Minh"),
                eq(expectedStatuses),
                eq(pageable)
        )).thenReturn(ticketPage);
        when(ticketMapper.toTicketResponse(ticket)).thenReturn(expectedResponse);

        for (String variant : List.of("TP.HCM", "Thành phố Hồ Chí Minh", "Hồ Chí Minh")) {
            mockUser.setProvince(variant);
            org.springframework.data.domain.Page<TicketResponse> result =
                    ticketService.getLifeThreateningTicketsInSameWard(pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(ticket.getTicketId());
        }

        verify(ticketRepository, times(3)).findLifeThreateningTicketsByWard(
                "Phường Bến Nghé", "Bến Nghé", "Hồ Chí Minh", expectedStatuses, pageable
        );
    }

    @Test
    @DisplayName("createTicket_normalizedPrefixAndUserDepartmentFallback_assignsSuccessfully")
    void createTicket_normalizedPrefixAndUserDepartmentFallback_assignsSuccessfully() {
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));
        createRequest.setPriority(Priority.LOW);
        createRequest.setConfidenceScore(95.0);
        createRequest.setLatitude(10.776530);
        createRequest.setLongitude(106.700980);
        createRequest.setAddress("12 Lê Lợi, P. Bến Nghé, TP. Hồ Chí Minh");

        SubCategory validSub = SubCategory.builder().subCategoryId(subId).category(mockCategory).isActive(true).build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(validSub));

        when(locationService.getBoundaryFromCoordinates(10.776530, 106.700980))
                .thenReturn(com.cityfix.backend.model.dto.response.LocationBoundary.builder()
                        .province("TP. Hồ Chí Minh")
                        .ward("P. Bến Nghé")
                        .build());

        UUID deptId = UUID.randomUUID();
        Department dept = Department.builder().departmentId(deptId).name("UBND Phường Bến Nghé").build();
        RoutingRule rule = RoutingRule.builder()
                .department(dept)
                .category(mockCategory)
                .province("Thành phố Hồ Chí Minh")
                .ward("Phường Bến Nghé")
                .build();

        when(routingRuleRepository.findByCategoryCategoryIdAndProvinceAndWardAndIsDeletedFalse(categoryId, "TP. Hồ Chí Minh", "P. Bến Nghé"))
                .thenReturn(Optional.empty());
        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(categoryId))
                .thenReturn(List.of(rule));

        // Bảng chính user_account không có staff, fallback sang bảng phụ user_department
        when(userRepository.findRepresentativeStaffByDepartmentId(deptId)).thenReturn(Optional.empty());
        User secondaryStaff = User.builder().userId(UUID.randomUUID()).fullName("Cán bộ kiêm nhiệm").build();
        when(userDepartmentRepository.findActiveStaffByDepartmentId(deptId)).thenReturn(List.of(secondaryStaff));

        Step stepEntity = Step.builder().stepId(UUID.randomUUID()).subCategory(validSub).stepName("Bước 1").build();
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subId)).thenReturn(List.of(stepEntity));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = ticketService.createTicket(createRequest);

        assertThat(response).isNotNull();
        verify(userDepartmentRepository).findActiveStaffByDepartmentId(deptId);
        verify(ticketStepRepository, atLeastOnce()).save(argThat(ts -> ts != null && secondaryStaff.equals(ts.getAssignee())));
    }

    @Test
    @DisplayName("getTopUpvotedTicketsInSameWard_success_preservesRankingOrderAndSetsUpvoteCounts")
    void getTopUpvotedTicketsInSameWard_success_preservesRankingOrderAndSetsUpvoteCounts() {
        mockUser.setWard("Phường 1");
        mockUser.setProvince("TP.HCM");

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        com.cityfix.backend.repository.projection.TopUpvotedTicketProjection p1 =
                mock(com.cityfix.backend.repository.projection.TopUpvotedTicketProjection.class);
        when(p1.getTicketId()).thenReturn(id1);
        when(p1.getUpvoteCount()).thenReturn(25L);

        com.cityfix.backend.repository.projection.TopUpvotedTicketProjection p2 =
                mock(com.cityfix.backend.repository.projection.TopUpvotedTicketProjection.class);
        when(p2.getTicketId()).thenReturn(id2);
        when(p2.getUpvoteCount()).thenReturn(10L);

        com.cityfix.backend.repository.projection.TopUpvotedTicketProjection p3 =
                mock(com.cityfix.backend.repository.projection.TopUpvotedTicketProjection.class);
        when(p3.getTicketId()).thenReturn(id3);
        when(p3.getUpvoteCount()).thenReturn(0L);

        List<TicketStatus> expectedStatuses = List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
        org.springframework.data.domain.Pageable top3Pageable = org.springframework.data.domain.PageRequest.of(0, 3);

        when(ticketRepository.findTopUpvotedTicketIdsByWard(
                eq("Phường 1"),
                eq("Phường 1"),
                eq("Hồ Chí Minh"),
                eq(expectedStatuses),
                eq(top3Pageable)
        )).thenReturn(List.of(p1, p2, p3));

        Ticket t1 = Ticket.builder().ticketId(id1).status(TicketStatus.IN_PROGRESS).build();
        Ticket t2 = Ticket.builder().ticketId(id2).status(TicketStatus.ASSIGNED).build();
        Ticket t3 = Ticket.builder().ticketId(id3).status(TicketStatus.ASSIGNED).build();

        // Giả lập query IN (...) trả về không đúng thứ tự (t3, t1, t2)
        when(ticketRepository.findAllWithSubCategoriesByTicketIdIn(List.of(id1, id2, id3)))
                .thenReturn(List.of(t3, t1, t2));

        when(ticketMapper.toTicketResponse(t1)).thenReturn(TicketResponse.builder().id(id1).status(TicketStatus.IN_PROGRESS).build());
        when(ticketMapper.toTicketResponse(t2)).thenReturn(TicketResponse.builder().id(id2).status(TicketStatus.ASSIGNED).build());
        when(ticketMapper.toTicketResponse(t3)).thenReturn(TicketResponse.builder().id(id3).status(TicketStatus.ASSIGNED).build());

        List<TicketResponse> result = ticketService.getTopUpvotedTicketsInSameWard();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(TicketResponse::getId).containsExactly(id1, id2, id3);
        assertThat(result).extracting(TicketResponse::getUpvoteCount).containsExactly(25L, 10L, 0L);

        verify(ticketRepository).findTopUpvotedTicketIdsByWard(
                "Phường 1", "Phường 1", "Hồ Chí Minh", expectedStatuses, top3Pageable
        );
        verify(ticketRepository).findAllWithSubCategoriesByTicketIdIn(List.of(id1, id2, id3));
    }

    @Test
    @DisplayName("getTopUpvotedTicketsInSameWard_nullOrBlankWard_returnsEmptyListWithoutQuerying")
    void getTopUpvotedTicketsInSameWard_nullOrBlankWard_returnsEmptyListWithoutQuerying() {
        mockUser.setWard("   ");

        List<TicketResponse> result = ticketService.getTopUpvotedTicketsInSameWard();

        assertThat(result).isEmpty();
        verify(ticketRepository, never()).findTopUpvotedTicketIdsByWard(any(), any(), any(), any(), any());
        verify(ticketRepository, never()).findAllWithSubCategoriesByTicketIdIn(any());
    }

    @Test
    @DisplayName("getTopUpvotedTicketsInSameWard_fewerThan3TicketsInWard_returnsAvailableTickets")
    void getTopUpvotedTicketsInSameWard_fewerThan3TicketsInWard_returnsAvailableTickets() {
        mockUser.setWard("P. Bến Nghé");
        mockUser.setProvince("Thành phố Hồ Chí Minh");

        UUID id1 = UUID.randomUUID();
        com.cityfix.backend.repository.projection.TopUpvotedTicketProjection p1 =
                mock(com.cityfix.backend.repository.projection.TopUpvotedTicketProjection.class);
        when(p1.getTicketId()).thenReturn(id1);
        when(p1.getUpvoteCount()).thenReturn(0L);

        List<TicketStatus> expectedStatuses = List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
        org.springframework.data.domain.Pageable top3Pageable = org.springframework.data.domain.PageRequest.of(0, 3);

        when(ticketRepository.findTopUpvotedTicketIdsByWard(
                "P. Bến Nghé", "Bến Nghé", "Hồ Chí Minh", expectedStatuses, top3Pageable
        )).thenReturn(List.of(p1));

        Ticket t1 = Ticket.builder().ticketId(id1).status(TicketStatus.ASSIGNED).build();
        when(ticketRepository.findAllWithSubCategoriesByTicketIdIn(List.of(id1))).thenReturn(List.of(t1));
        when(ticketMapper.toTicketResponse(t1)).thenReturn(TicketResponse.builder().id(id1).status(TicketStatus.ASSIGNED).build());

        List<TicketResponse> result = ticketService.getTopUpvotedTicketsInSameWard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(id1);
        assertThat(result.get(0).getUpvoteCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("getTopUpvotedTicketsInSameWard_noMatchingTickets_returnsEmptyWithoutHydrationQuery")
    void getTopUpvotedTicketsInSameWard_noMatchingTickets_returnsEmptyWithoutHydrationQuery() {
        mockUser.setWard("Phường Đa Kao");
        mockUser.setProvince(null);

        when(ticketRepository.findTopUpvotedTicketIdsByWard(
                eq("Phường Đa Kao"),
                eq("Đa Kao"),
                eq(""),
                eq(List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS)),
                eq(org.springframework.data.domain.PageRequest.of(0, 3))
        )).thenReturn(java.util.Collections.emptyList());

        List<TicketResponse> result = ticketService.getTopUpvotedTicketsInSameWard();

        assertThat(result).isEmpty();
        verify(ticketRepository, never()).findAllWithSubCategoriesByTicketIdIn(any());
    }

    @Test
    @DisplayName("getTopUpvotedTicketsInSameWard_numericXaAndThiTran_preservesOriginalAdministrativePrefix")
    void getTopUpvotedTicketsInSameWard_numericXaAndThiTran_preservesOriginalAdministrativePrefix() {
        List<TicketStatus> expectedStatuses = List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
        org.springframework.data.domain.Pageable top3Pageable = org.springframework.data.domain.PageRequest.of(0, 3);

        mockUser.setProvince("Tỉnh Lâm Đồng");

        mockUser.setWard("Xã 1");
        when(ticketRepository.findTopUpvotedTicketIdsByWard("Xã 1", "Xã 1", "Lâm Đồng", expectedStatuses, top3Pageable))
                .thenReturn(java.util.Collections.emptyList());
        assertThat(ticketService.getTopUpvotedTicketsInSameWard()).isEmpty();
        verify(ticketRepository).findTopUpvotedTicketIdsByWard("Xã 1", "Xã 1", "Lâm Đồng", expectedStatuses, top3Pageable);

        mockUser.setWard("Thị trấn 2");
        when(ticketRepository.findTopUpvotedTicketIdsByWard("Thị trấn 2", "Thị trấn 2", "Lâm Đồng", expectedStatuses, top3Pageable))
                .thenReturn(java.util.Collections.emptyList());
        assertThat(ticketService.getTopUpvotedTicketsInSameWard()).isEmpty();
        verify(ticketRepository).findTopUpvotedTicketIdsByWard("Thị trấn 2", "Thị trấn 2", "Lâm Đồng", expectedStatuses, top3Pageable);

        mockUser.setWard("TT. 2");
        when(ticketRepository.findTopUpvotedTicketIdsByWard("TT. 2", "Thị trấn 2", "Lâm Đồng", expectedStatuses, top3Pageable))
                .thenReturn(java.util.Collections.emptyList());
        assertThat(ticketService.getTopUpvotedTicketsInSameWard()).isEmpty();
        verify(ticketRepository).findTopUpvotedTicketIdsByWard("TT. 2", "Thị trấn 2", "Lâm Đồng", expectedStatuses, top3Pageable);
    }

    @Test
    @DisplayName("getTopUpvotedTicketsInSameWard_unauthenticated_throwsUnauthenticated")
    void getTopUpvotedTicketsInSameWard_unauthenticated_throwsUnauthenticated() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> ticketService.getTopUpvotedTicketsInSameWard())
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHENTICATED);

        verify(ticketRepository, never()).findTopUpvotedTicketIdsByWard(any(), any(), any(), any(), any());
    }
}

