package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateRatingRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.RatingResponse;
import com.cityfix.backend.service.RatingService;
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

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingControllerTest {

    @Mock
    private RatingService ratingService;

    @InjectMocks
    private RatingController ratingController;

    private static Validator validator;

    private UUID ticketId;
    private CreateRatingRequest validRequest;
    private RatingResponse mockResponse;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();

        validRequest = CreateRatingRequest.builder()
                .rate(5)
                .isAnonymous(false)
                .feedback("Dịch vụ xử lý rất tốt và nhanh chóng.")
                .build();

        mockResponse = RatingResponse.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .userId(UUID.randomUUID())
                .userName("Nguyễn Văn A")
                .rate(5)
                .isAnonymous(false)
                .feedback("Dịch vụ xử lý rất tốt và nhanh chóng.")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createRating_validRequest_returns200Ok")
    void createRating_validRequest_returns200Ok() {
        when(ratingService.createRating(eq(ticketId), any(CreateRatingRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<RatingResponse>> response = ratingController.createRating(ticketId, validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getRate()).isEqualTo(5);
        assertThat(response.getBody().getData().getFeedback()).isEqualTo("Dịch vụ xử lý rất tốt và nhanh chóng.");

        verify(ratingService, times(1)).createRating(eq(ticketId), eq(validRequest));
    }

    @Test
    @DisplayName("validation_rateNull_producesConstraintViolation")
    void validation_rateNull_producesConstraintViolation() {
        CreateRatingRequest invalidRequest = CreateRatingRequest.builder()
                .rate(null)
                .build();

        Set<ConstraintViolation<CreateRatingRequest>> violations = validator.validate(invalidRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rate"));
    }

    @Test
    @DisplayName("validation_rateBelow1_producesConstraintViolation")
    void validation_rateBelow1_producesConstraintViolation() {
        CreateRatingRequest invalidRequest = CreateRatingRequest.builder()
                .rate(0)
                .build();

        Set<ConstraintViolation<CreateRatingRequest>> violations = validator.validate(invalidRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rate"));
    }

    @Test
    @DisplayName("validation_rateAbove5_producesConstraintViolation")
    void validation_rateAbove5_producesConstraintViolation() {
        CreateRatingRequest invalidRequest = CreateRatingRequest.builder()
                .rate(6)
                .build();

        Set<ConstraintViolation<CreateRatingRequest>> violations = validator.validate(invalidRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rate"));
    }

    @Test
    @DisplayName("validation_feedbackExceeds1000Chars_producesConstraintViolation")
    void validation_feedbackExceeds1000Chars_producesConstraintViolation() {
        String longFeedback = "a".repeat(1001);
        CreateRatingRequest invalidRequest = CreateRatingRequest.builder()
                .rate(4)
                .feedback(longFeedback)
                .build();

        Set<ConstraintViolation<CreateRatingRequest>> violations = validator.validate(invalidRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("feedback"));
    }
}
