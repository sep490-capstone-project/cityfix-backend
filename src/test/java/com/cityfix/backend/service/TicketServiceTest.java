package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.*;
import com.cityfix.backend.model.enums.AssignmentStatus;
import com.cityfix.backend.model.enums.Priority;
import com.cityfix.backend.model.enums.LocationMethod;
import com.cityfix.backend.model.enums.SeverityLevel;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private com.cityfix.backend.mapper.DepartmentMapper departmentMapper;

    @Mock
    private com.cityfix.backend.mapper.TicketStepMapper ticketStepMapper;

    @Mock
    private com.cityfix.backend.mapper.TicketHistoryMapper ticketHistoryMapper;
    
    @Mock
    private com.cityfix.backend.repository.NotificationRepository notificationRepository;
    
    @Mock
    private com.cityfix.backend.mapper.NotificationMapper notificationMapper;
    
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private com.cityfix.backend.service.EmailService emailService;

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

    @BeforeEach
    void setUp() {
        email = "resident@cityfix.com";
        categoryId = UUID.randomUUID();

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
        createRequest.setCategoryId(categoryId);
        createRequest.setSeverity(SeverityLevel.HIGH);
        createRequest.setPriority(Priority.HIGH);
        createRequest.setLocationMethod(LocationMethod.AUTO_GPS);
        createRequest.setAddress("268 Lý Thường Kiệt, Q10");
        createRequest.setLatitude(10.762622);
        createRequest.setLongitude(106.660172);

        mockTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description(createRequest.getDescription())
                .category(mockCategory)
                .severity(createRequest.getSeverity())
                .priority(createRequest.getPriority())
                .locationMethod(createRequest.getLocationMethod())
                .address(createRequest.getAddress())
                .status(TicketStatus.INITIATED)
                .user(mockUser)
                .build();

        lenient().when(ticketMapper.toTicket(any(), any(), any(), any(), any(), anyBoolean())).thenAnswer(invocation -> {
            CreateTicketRequest req = invocation.getArgument(0);
            Category cat = invocation.getArgument(1);
            User u = invocation.getArgument(2);
            org.locationtech.jts.geom.Point loc = invocation.getArgument(3);
            TicketStatus st = invocation.getArgument(4);
            boolean needsRev = invocation.getArgument(5);

            return Ticket.builder()
                    .description(req.getDescription())
                    .category(cat)
                    .severity(req.getSeverity())
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

        lenient().when(ticketStepMapper.toTicketStep(any(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            Step s = invocation.getArgument(1);
            User staff = invocation.getArgument(2);
            AssignmentStatus st = invocation.getArgument(3);
            int idx = invocation.getArgument(4);
            return TicketStep.builder()
                    .ticket(t)
                    .step(s)
                    .assignee(staff)
                    .status(st)
                    .orderIndex(idx)
                    .build();
        });

        mockResponse = TicketResponse.builder()
                .id(mockTicket.getTicketId())
                .description(mockTicket.getDescription())
                .imageUrl(createRequest.getImageUrl())
                .categoryId(categoryId)
                .severity(createRequest.getSeverity())
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
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.createTicket(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).isEqualTo("Mặt đường bị sụt hố sâu lớn");
        assertThat(result.getCreatedBy()).isEqualTo(mockUser.getUserId());
        assertThat(result.getImageUrl()).isEqualTo(createRequest.getImageUrl());
        assertThat(result.getLocationMethod()).isEqualTo(LocationMethod.AUTO_GPS);

        verify(categoryRepository, times(1)).findById(categoryId);
        verify(ticketRepository, times(1)).save(any(Ticket.class));
        verify(reportImageRepository, times(1)).save(any());
        verify(ticketMapper, times(1)).toTicketResponse(any(Ticket.class));
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
    @DisplayName("createTicket_categoryNotFound_throwsCategoryNotFoundAppException")
    void createTicket_categoryNotFound_throwsCategoryNotFoundAppException() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_subCategoryIdsContainNull_throwsINVALID_REQUEST")
    void createTicket_subCategoryIdsContainNull_throwsINVALID_REQUEST() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
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
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
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
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
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
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
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
    @DisplayName("createTicket_subCategoryCategoryMismatch_throwsINVALID_REQUEST")
    void createTicket_subCategoryCategoryMismatch_throwsINVALID_REQUEST() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));

        Category otherCat = Category.builder().categoryId(UUID.randomUUID()).build();
        SubCategory mismatchedSub = SubCategory.builder()
                .subCategoryId(subId)
                .category(otherCat)
                .isActive(true)
                .build();
        when(subCategoryRepository.findById(subId)).thenReturn(Optional.of(mismatchedSub));

        assertThatThrownBy(() -> ticketService.createTicket(createRequest))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_validSubCategories_persistsTicketSubCategoriesAndStepsWhenRouted")
    void createTicket_validSubCategories_persistsTicketSubCategoriesAndStepsWhenRouted() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
        UUID subId = UUID.randomUUID();
        createRequest.setSubCategoryIds(List.of(subId));
        createRequest.setPriority(Priority.LOW);
        createRequest.setSeverity(SeverityLevel.LOW);
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
        when(stepRepository.findBySubCategory_SubCategoryId(subId)).thenReturn(List.of(stepEntity));

        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse response = ticketService.createTicket(createRequest);

        assertThat(response).isNotNull();
        verify(ticketSubCategoryRepository, atLeastOnce()).save(any());
        verify(ticketStepRepository, atLeastOnce()).save(argThat(ts -> ts != null && ts.getAssignee() != null));
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

        when(ticketRepository.findDuplicateTickets(latitude, longitude, categoryId))
                .thenReturn(List.of(mockProjection));
        when(ticketMapper.toDuplicateTicketResponseList(List.of(mockProjection)))
                .thenReturn(List.of(mockDuplicateResponse));

        List<DuplicateTicketResponse> result = ticketService.findDuplicateTickets(latitude, longitude, categoryId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicketId()).isEqualTo(mockDuplicateResponse.getTicketId());
        assertThat(result.get(0).getDistanceMeters()).isEqualTo(18.5);
        assertThat(result.get(0).getImageUrl()).isEqualTo("https://cloudinary.com/image.jpg");

        verify(ticketRepository, times(1)).findDuplicateTickets(latitude, longitude, categoryId);
    }

    @Test
    @DisplayName("findDuplicateTickets_missingLatitude_throwsValidationAppException")
    void findDuplicateTickets_missingLatitude_throwsValidationAppException() {
        assertThatThrownBy(() -> ticketService.findDuplicateTickets(null, 106.660172, categoryId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(ticketRepository, never()).findDuplicateTickets(any(), any(), any());
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
    @DisplayName("reviewTicket_validData_returnsUpdatedTicket")
    void reviewTicket_validData_returnsUpdatedTicket() {
        mockUser.setRole(new Role(UUID.randomUUID(), "ADMIN"));
        mockTicket.setStatus(TicketStatus.INITIATED);
        
        UUID deptId = UUID.randomUUID();
        User staff = new User();
        staff.setUserId(UUID.randomUUID());

        SubCategory subCat = SubCategory.builder()
                .subCategoryId(UUID.randomUUID())
                .name("Hố ga mất nắp")
                .build();
        TicketSubCategory tsc = TicketSubCategory.builder()
                .ticket(mockTicket)
                .subCategory(subCat)
                .build();
        mockTicket.setSubCategories(new ArrayList<>(List.of(tsc)));

        Step stepDef = Step.builder()
                .stepId(UUID.randomUUID())
                .subCategory(subCat)
                .stepName("Khảo sát")
                .build();
        when(stepRepository.findBySubCategory_SubCategoryId(subCat.getSubCategoryId())).thenReturn(List.of(stepDef));
        when(userRepository.findRepresentativeStaffByDepartmentId(deptId)).thenReturn(Optional.of(staff));

        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = new com.cityfix.backend.model.dto.request.ReviewTicketRequest();
        request.setTargetDepartmentId(deptId);
        request.setIsLifeThreatening(true);
        request.setReviewNote("Khẩn cấp");

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.reviewTicket(mockTicket.getTicketId(), request);

        assertThat(result).isNotNull();
        verify(ticketRepository, times(1)).save(mockTicket);
        assertThat(mockTicket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(mockTicket.getNeedsManualReview()).isFalse();
        assertThat(mockTicket.getReviewNote()).isEqualTo("Khẩn cấp");
        assertThat(mockTicket.getSteps()).hasSize(1);
    }

    @Test
    @DisplayName("reviewTicket_notInitiated_throwsException")
    void reviewTicket_notInitiated_throwsException() {
        mockUser.setRole(new Role(UUID.randomUUID(), "ADMIN"));
        mockTicket.setStatus(TicketStatus.OPEN); // Not INITIATED
        
        com.cityfix.backend.model.dto.request.ReviewTicketRequest request = new com.cityfix.backend.model.dto.request.ReviewTicketRequest();
        
        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));

        assertThatThrownBy(() -> ticketService.reviewTicket(mockTicket.getTicketId(), request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_INITIATED);
    }

    @Test
    @DisplayName("createTicket_noRoutingRuleMatches_setsInitiatedAndNeedsManualReview")
    void createTicket_noRoutingRuleMatches_setsInitiatedAndNeedsManualReview() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
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
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
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

        mockTicket.setStatus(TicketStatus.OPEN);

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
        when(stepRepository.findBySubCategory_SubCategoryId(subCat.getSubCategoryId())).thenReturn(List.of(mockStepEntity));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(targetDeptId)).thenReturn(Optional.of(repStaff));

        TicketStep activeStep = TicketStep.builder()
                .ticket(mockTicket)
                .step(mockStepEntity)
                .status(AssignmentStatus.OPEN)
                .orderIndex(0)
                .build();
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByOrderIndexAsc(eq(mockTicket.getTicketId()), anyList()))
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
    @DisplayName("getAssignedTicketsForStaff_validStaff_success")
    void getAssignedTicketsForStaff_validStaff_success() {
        com.cityfix.backend.model.entity.Department dept = new com.cityfix.backend.model.entity.Department();
        dept.setDepartmentId(UUID.randomUUID());
        dept.setName("Phòng Giao thông");
        mockUser.setDepartment(dept);

        com.cityfix.backend.model.entity.TicketStep step =
                com.cityfix.backend.model.entity.TicketStep.builder()
                        .ticket(mockTicket)
                        .status(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED)
                        .build();

        org.springframework.data.domain.Page<com.cityfix.backend.model.entity.Ticket> page =
                new org.springframework.data.domain.PageImpl<>(List.of(mockTicket));

        when(ticketStepRepository.findDistinctTicketsByAssigneeUserIdAndStatusIn(any(), any(), any())).thenReturn(page);
        when(ticketMapper.toTicketResponse(mockTicket)).thenReturn(mockResponse);

        org.springframework.data.domain.Page<TicketResponse> result =
                ticketService.getAssignedTicketsForStaff(null, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
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
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.OPEN);
        mockTicket.setSteps(new ArrayList<>(List.of(step)));

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByOrderIndexAsc(eq(mockTicket.getTicketId()), any()))
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
        when(stepRepository.findBySubCategory_SubCategoryId(subCat.getSubCategoryId())).thenReturn(List.of(mockStepEntity));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(suggestedDeptId)).thenReturn(Optional.of(repStaff));

        com.cityfix.backend.model.entity.TicketStep step = new com.cityfix.backend.model.entity.TicketStep();
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(departmentRepository.findById(suggestedDeptId)).thenReturn(Optional.of(suggestedDept));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByOrderIndexAsc(eq(mockTicket.getTicketId()), any()))
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
        when(stepRepository.findBySubCategory_SubCategoryId(subCat.getSubCategoryId())).thenReturn(List.of(mockStepEntity));

        User repStaff = new User();
        repStaff.setUserId(UUID.randomUUID());
        when(userRepository.findRepresentativeStaffByDepartmentId(suggestedDeptId)).thenReturn(Optional.of(repStaff));

        com.cityfix.backend.model.dto.response.DepartmentResponse deptResp = new com.cityfix.backend.model.dto.response.DepartmentResponse(
            suggestedDeptId, "Name", null, "email", "phone", 0, true
        );

        com.cityfix.backend.model.entity.TicketStep step = new com.cityfix.backend.model.entity.TicketStep();
        step.setStatus(com.cityfix.backend.model.enums.AssignmentStatus.ASSIGNED);

        when(ticketRepository.findById(mockTicket.getTicketId())).thenReturn(Optional.of(mockTicket));
        when(categoryRepository.findById(suggestedCategoryId)).thenReturn(Optional.of(suggestedCategory));
        when(ticketRepository.save(mockTicket)).thenReturn(mockTicket);

        when(routingRuleRepository.findByCategoryCategoryIdAndIsDeletedFalse(suggestedCategoryId))
            .thenReturn(List.of(com.cityfix.backend.model.entity.RoutingRule.builder().department(suggestedDept).build()));

        when(departmentMapper.toResponseList(any())).thenReturn(List.of(deptResp));
        when(departmentRepository.findById(suggestedDeptId)).thenReturn(Optional.of(suggestedDept));
        when(ticketStepRepository.findByTicketTicketIdAndStatusInOrderByOrderIndexAsc(eq(mockTicket.getTicketId()), any()))
                .thenReturn(List.of(step));

        TicketResponse mockResponse = TicketResponse.builder().build();
        when(ticketMapper.toTicketResponse(any())).thenReturn(mockResponse);

        com.cityfix.backend.model.dto.request.RejectTicketRequest req = new com.cityfix.backend.model.dto.request.RejectTicketRequest(suggestedCategoryId, null, "Reason");

        TicketResponse res = ticketService.forwardTicket(mockTicket.getTicketId(), req);

        assertThat(res).isNotNull();
        assertThat(mockTicket.getCategory()).isEqualTo(suggestedCategory);
        verify(categoryRepository).findById(suggestedCategoryId);
    }
}
