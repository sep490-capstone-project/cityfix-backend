package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateTicketRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DuplicateTicketResponse;
import com.cityfix.backend.model.dto.response.ImageUploadResponse;
import com.cityfix.backend.model.dto.response.TicketResponse;
import com.cityfix.backend.model.enums.DangerLevel;
import com.cityfix.backend.model.enums.LocationMethod;
import com.cityfix.backend.model.enums.SeverityLevel;
import com.cityfix.backend.model.enums.TicketStatus;
import com.cityfix.backend.service.AiService;
import com.cityfix.backend.service.TicketService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private AiService aiService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TicketController ticketController;

    private static Validator validator;

    private CreateTicketRequest validCreateRequest;
    private TicketResponse mockTicketResponse;
    private UUID categoryId;
    private String email;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        email = "resident@cityfix.com";
        categoryId = UUID.randomUUID();

        validCreateRequest = new CreateTicketRequest();
        validCreateRequest.setTitle("Mặt đường bị hỏng");
        validCreateRequest.setDescription("Mặt đường xuất hiện ổ gà lớn");
        validCreateRequest.setImageUrl("https://cloudinary.com/test.jpg");
        validCreateRequest.setCategoryId(categoryId);
        validCreateRequest.setSeverity(SeverityLevel.MEDIUM);
        validCreateRequest.setDangerLevel(DangerLevel.CAUTION);
        validCreateRequest.setLocationMethod(LocationMethod.AUTO_GPS);
        validCreateRequest.setAddress("123 Nguyễn Huệ, Q1");
        validCreateRequest.setLatitude(10.776889);
        validCreateRequest.setLongitude(106.700806);

        mockTicketResponse = TicketResponse.builder()
                .id(UUID.randomUUID())
                .title(validCreateRequest.getTitle())
                .description(validCreateRequest.getDescription())
                .imageUrl(validCreateRequest.getImageUrl())
                .categoryId(categoryId)
                .severity(validCreateRequest.getSeverity())
                .dangerLevel(validCreateRequest.getDangerLevel())
                .locationMethod(validCreateRequest.getLocationMethod())
                .address(validCreateRequest.getAddress())
                .status(TicketStatus.INITIATED)
                .latitude(validCreateRequest.getLatitude())
                .longitude(validCreateRequest.getLongitude())
                .createdBy(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==========================================
    // 1. Create Ticket Tests
    // ==========================================

    @Test
    @DisplayName("createTicket_validData_returns200OKWrappedInBaseResponse")
    void createTicket_validData_returns200OKWrappedInBaseResponse() {
        when(authentication.getName()).thenReturn(email);
        when(ticketService.createTicket(any(CreateTicketRequest.class), eq(email)))
                .thenReturn(mockTicketResponse);

        ResponseEntity<BaseResponse<TicketResponse>> response =
                ticketController.createTicket(validCreateRequest, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Success");
        assertThat(response.getBody().getData().getTitle()).isEqualTo("Mặt đường bị hỏng");
        assertThat(response.getBody().getData().getLatitude()).isEqualTo(10.776889);

        verify(ticketService, times(1)).createTicket(validCreateRequest, email);
    }

    @Test
    @DisplayName("validation_invalidLatitudeRange_producesConstraintViolation")
    void validation_invalidLatitudeRange_producesConstraintViolation() {
        validCreateRequest.setLatitude(150.0); // Out of [-90, 90]

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(validCreateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Vĩ độ không hợp lệ"));
    }

    @Test
    @DisplayName("validation_invalidLongitudeRange_producesConstraintViolation")
    void validation_invalidLongitudeRange_producesConstraintViolation() {
        validCreateRequest.setLongitude(200.0); // Out of [-180, 180]

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(validCreateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Kinh độ không hợp lệ"));
    }

    @Test
    @DisplayName("validation_missingTitle_producesConstraintViolation")
    void validation_missingTitle_producesConstraintViolation() {
        validCreateRequest.setTitle("");

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(validCreateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Title is required"));
    }

    @Test
    @DisplayName("validation_missingLatitude_producesConstraintViolation")
    void validation_missingLatitude_producesConstraintViolation() {
        validCreateRequest.setLatitude(null);

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(validCreateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Latitude is required"));
    }

    // ==========================================
    // 2. Upload Image Tests
    // ==========================================

    @Test
    @DisplayName("uploadImage_validMultipartFile_returns200WithData")
    void uploadImage_validMultipartFile_returns200WithData() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        ImageUploadResponse mockImageResponse = ImageUploadResponse.builder()
                .publicId("cityfix/reports/img_123")
                .secureUrl("https://res.cloudinary.com/cityfix/image/upload/v1/cityfix/reports/img_123.jpg")
                .format("jpg")
                .width(1920)
                .height(1080)
                .bytes(1024L)
                .createdAt("2026-09-11T12:00:00Z")
                .build();

        when(ticketService.uploadImage(any(), eq("cityfix/reports"))).thenReturn(mockImageResponse);

        ResponseEntity<BaseResponse<ImageUploadResponse>> responseEntity =
                ticketController.uploadImage(file, "cityfix/reports");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().isSuccess()).isTrue();
        assertThat(responseEntity.getBody().getMessage()).isEqualTo("Tải ảnh lên thành công.");
        assertThat(responseEntity.getBody().getData()).isEqualTo(mockImageResponse);

        verify(ticketService, times(1)).uploadImage(file, "cityfix/reports");
    }

    // ==========================================
    // 3. Check Duplicate Tickets Tests
    // ==========================================

    @Test
    @DisplayName("checkDuplicates_validParams_returns200WithDuplicateList")
    void checkDuplicates_validParams_returns200WithDuplicateList() {
        Double latitude = 10.762622;
        Double longitude = 106.660172;

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

        when(ticketService.findDuplicateTickets(latitude, longitude, categoryId))
                .thenReturn(List.of(mockDuplicateResponse));

        ResponseEntity<BaseResponse<List<DuplicateTicketResponse>>> response =
                ticketController.checkDuplicates(null, latitude, longitude, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Kiểm tra vé trùng lặp thành công.");
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getTicketId()).isEqualTo(mockDuplicateResponse.getTicketId());

        verify(ticketService, times(1)).findDuplicateTickets(latitude, longitude, categoryId);
    }

    @Test
    @DisplayName("checkDuplicates_validLocationString_returns200WithDuplicateList")
    void checkDuplicates_validLocationString_returns200WithDuplicateList() {
        String locationStr = "10.762622,106.660172";
        Double latitude = 10.762622;
        Double longitude = 106.660172;

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

        when(ticketService.findDuplicateTickets(latitude, longitude, categoryId))
                .thenReturn(List.of(mockDuplicateResponse));

        ResponseEntity<BaseResponse<List<DuplicateTicketResponse>>> response =
                ticketController.checkDuplicates(locationStr, null, null, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Kiểm tra vé trùng lặp thành công.");
        assertThat(response.getBody().getData()).hasSize(1);

        verify(ticketService, times(1)).findDuplicateTickets(latitude, longitude, categoryId);
    }

    @Test
    @DisplayName("checkDuplicates_hexEwkbLocationString_returns200WithDuplicateList")
    void checkDuplicates_hexEwkbLocationString_returns200WithDuplicateList() {
        // HexEWKB for Point(106.700806, 10.776889) SRID=4326
        String hexEwkb = "0101000020E61000000D71AC8BDBAC5A40598638D6C58D2540";

        DuplicateTicketResponse mockDuplicateResponse = DuplicateTicketResponse.builder()
                .ticketId(UUID.randomUUID())
                .description("Sự cố giao thông")
                .status("INITIATED")
                .build();

        when(ticketService.findDuplicateTickets(any(Double.class), any(Double.class), eq(categoryId)))
                .thenReturn(List.of(mockDuplicateResponse));

        ResponseEntity<BaseResponse<List<DuplicateTicketResponse>>> response =
                ticketController.checkDuplicates(hexEwkb, null, null, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);

        verify(ticketService, times(1)).findDuplicateTickets(any(Double.class), any(Double.class), eq(categoryId));
    }
}
