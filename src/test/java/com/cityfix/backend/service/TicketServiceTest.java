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
    private AiAnalysisRepository aiAnalysisRepository;

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
        createRequest.setTitle("Hố sâu nguy hiểm");
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
                .title(createRequest.getTitle())
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
                .title(mockTicket.getTitle())
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
        assertThat(result.getTitle()).isEqualTo("Hố sâu nguy hiểm");
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
    // 2. Upload Image Service Tests
    // ==========================================

    @Test
    @DisplayName("uploadImage_validFile_success")
    void uploadImage_validFile_success() throws IOException {
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
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

        ImageUploadResponse actualResponse = ticketService.uploadImage(validFile, "cityfix/reports");

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getPublicId()).isEqualTo("cityfix/reports/img_123");
        assertThat(actualResponse.getSecureUrl()).contains("cityfix/reports/img_123.jpg");

        verify(uploader, times(1)).upload(any(byte[].class), any(Map.class));
        verify(imageMapper, times(1)).toImageUploadResponse(mockCloudinaryResponse);
    }

    @Test
    @DisplayName("uploadImage_invalidFormat_throwsAppException")
    void uploadImage_invalidFormat_throwsAppException() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        assertThatThrownBy(() -> ticketService.uploadImage(invalidFile, "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_exceedsMaxSize_throwsAppException")
    void uploadImage_exceedsMaxSize_throwsAppException() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "huge_photo.png",
                "image/png",
                largeContent
        );

        assertThatThrownBy(() -> ticketService.uploadImage(largeFile, "cityfix/reports"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

        verifyNoInteractions(cloudinary);
    }

    @Test
    @DisplayName("uploadImage_cloudinaryIOException_throwsAppException")
    void uploadImage_cloudinaryIOException_throwsAppException() throws IOException {
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "report_image.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> ticketService.uploadImage(validFile, "cityfix/reports"))
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
