package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.ImageMapper;
import com.cityfix.backend.mapper.TicketMapper;
import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Ticket;
import com.cityfix.backend.model.entity.User;
import com.cityfix.backend.model.enums.DangerLevel;
import com.cityfix.backend.model.enums.LocationMethod;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.repository.*;
import com.cityfix.backend.repository.projection.DuplicateTicketProjection;
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
    private ReportImageRepository reportImageRepository;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private ImageMapper imageMapper;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

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

        mockCategory = Category.builder()
                .categoryId(categoryId)
                .name("Hạ tầng giao thông")
                .build();

        createRequest = new CreateTicketRequest();
        createRequest.setDescription("Mặt đường bị sụt hố sâu lớn");
        createRequest.setImageUrl("https://cloudinary.com/image.jpg");
        createRequest.setCategoryId(categoryId);
        createRequest.setSeverity(SeverityLevel.HIGH);
        createRequest.setDangerLevel(DangerLevel.DANGEROUS);
        createRequest.setLocationMethod(LocationMethod.AUTO_GPS);
        createRequest.setAddress("268 Lý Thường Kiệt, Q10");
        createRequest.setLatitude(10.762622);
        createRequest.setLongitude(106.660172);

        mockTicket = Ticket.builder()
                .ticketId(UUID.randomUUID())
                .description(createRequest.getDescription())
                .category(mockCategory)
                .severity(createRequest.getSeverity())
                .dangerLevel(createRequest.getDangerLevel())
                .locationMethod(createRequest.getLocationMethod())
                .address(createRequest.getAddress())
                .status(TicketStatus.INITIATED)
                .user(mockUser)
                .build();

        mockResponse = TicketResponse.builder()
                .id(mockTicket.getTicketId())
                .description(mockTicket.getDescription())
                .imageUrl(createRequest.getImageUrl())
                .categoryId(categoryId)
                .severity(createRequest.getSeverity())
                .dangerLevel(createRequest.getDangerLevel())
                .locationMethod(createRequest.getLocationMethod())
                .address(createRequest.getAddress())
                .status(TicketStatus.INITIATED)
                .latitude(createRequest.getLatitude())
                .longitude(createRequest.getLongitude())
                .createdBy(mockUser.getUserId())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==========================================
    // 1. Create Ticket Service Tests
    // ==========================================

    @Test
    @DisplayName("createTicket_validData_returnsTicketResponse")
    void createTicket_validData_returnsTicketResponse() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mockCategory));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(mockTicket);
        when(ticketMapper.toTicketResponse(any(Ticket.class))).thenReturn(mockResponse);

        TicketResponse result = ticketService.createTicket(createRequest, email);

        assertThat(result).isNotNull();
        assertThat(result.getDescription()).isEqualTo("Mặt đường bị sụt hố sâu lớn");
        assertThat(result.getCreatedBy()).isEqualTo(mockUser.getUserId());
        assertThat(result.getImageUrl()).isEqualTo(createRequest.getImageUrl());
        assertThat(result.getLocationMethod()).isEqualTo(LocationMethod.AUTO_GPS);

        verify(userRepository, times(1)).findByEmail(email);
        verify(categoryRepository, times(1)).findById(categoryId);
        verify(ticketRepository, times(1)).save(any(Ticket.class));
        verify(reportImageRepository, times(1)).save(any());
        verify(ticketMapper, times(1)).toTicketResponse(any(Ticket.class));
    }

    @Test
    @DisplayName("createTicket_accountNotFound_throwsAccountNotFoundAppException")
    void createTicket_accountNotFound_throwsAccountNotFoundAppException() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createRequest, email))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket_categoryNotFound_throwsCategoryNotFoundAppException")
    void createTicket_categoryNotFound_throwsCategoryNotFoundAppException() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createRequest, email))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

        verify(ticketRepository, never()).save(any());
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
}
