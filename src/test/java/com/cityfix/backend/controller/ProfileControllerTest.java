package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.UpdateProfileRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.UserProfileResponse;
import com.cityfix.backend.service.UserService;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private ProfileController profileController;

    private static Validator validator;

    private UserProfileResponse mockProfileResponse;
    private UpdateProfileRequest mockUpdateRequest;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        mockProfileResponse = UserProfileResponse.builder()
                .fullName("Nguyen Van A")
                .phoneNumber("0912345678")
                .address("123 Nguyen Hue")
                .province("TP.HCM")
                .ward("Phuong Ben Nghe")
                .build();

        mockUpdateRequest = UpdateProfileRequest.builder()
                .fullName("Nguyen Van B")
                .phoneNumber("0987654321")
                .address("456 Le Loi")
                .province("TP.HCM")
                .ward("Phuong Ben Thanh")
                .build();
    }

    @Test
    @DisplayName("getMyProfile_returns200OKWithProfileResponse")
    void getMyProfile_returns200OKWithProfileResponse() {
        when(userService.getMyProfile()).thenReturn(mockProfileResponse);

        ResponseEntity<BaseResponse<UserProfileResponse>> response = profileController.getMyProfile();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Lấy thông tin profile thành công.");
        assertThat(response.getBody().getData().getFullName()).isEqualTo("Nguyen Van A");

        verify(userService, times(1)).getMyProfile();
    }

    @Test
    @DisplayName("updateMyProfile_validRequest_returns200OKWithUpdatedProfile")
    void updateMyProfile_validRequest_returns200OKWithUpdatedProfile() {
        UserProfileResponse updatedResponse = UserProfileResponse.builder()
                .fullName("Nguyen Van B")
                .phoneNumber("0987654321")
                .address("456 Le Loi")
                .province("TP.HCM")
                .ward("Phuong Ben Thanh")
                .build();

        when(userService.updateMyProfile(mockUpdateRequest)).thenReturn(updatedResponse);

        ResponseEntity<BaseResponse<UserProfileResponse>> response = profileController.updateMyProfile(mockUpdateRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Cập nhật profile thành công.");
        assertThat(response.getBody().getData().getFullName()).isEqualTo("Nguyen Van B");

        verify(userService, times(1)).updateMyProfile(mockUpdateRequest);
    }

    @Test
    @DisplayName("validation_blankPhoneNumber_producesConstraintViolation")
    void validation_blankPhoneNumber_producesConstraintViolation() {
        mockUpdateRequest.setPhoneNumber("");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(mockUpdateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Phone number cannot be blank"));
    }

    @Test
    @DisplayName("validation_invalidPhoneNumberPattern_producesConstraintViolation")
    void validation_invalidPhoneNumberPattern_producesConstraintViolation() {
        mockUpdateRequest.setPhoneNumber("123");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(mockUpdateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Phone number is invalid"));
    }

    @Test
    @DisplayName("validation_blankFullName_producesConstraintViolation")
    void validation_blankFullName_producesConstraintViolation() {
        mockUpdateRequest.setFullName("");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(mockUpdateRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Tên không được để trống"));
    }
}
