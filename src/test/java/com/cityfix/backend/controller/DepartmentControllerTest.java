package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.CreateDepartmentRequest;
import com.cityfix.backend.model.dto.request.UpdateDepartmentRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.DepartmentResponse;
import com.cityfix.backend.model.enums.DepartmentLevel;
import com.cityfix.backend.service.DepartmentService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentControllerTest {

    @Mock
    private DepartmentService departmentService;

    @InjectMocks
    private DepartmentController departmentController;

    private static Validator validator;

    private UUID departmentId;
    private CreateDepartmentRequest validCreateRequest;
    private UpdateDepartmentRequest validUpdateRequest;
    private DepartmentResponse mockResponse;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @BeforeEach
    void setUp() {
        departmentId = UUID.randomUUID();

        validCreateRequest = new CreateDepartmentRequest(
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                DepartmentLevel.WARD,
                "longtruong@cityfix.vn",
                "0901234567"
        );

        validUpdateRequest = new UpdateDepartmentRequest(
                "Phòng QLĐT Quận 9",
                DepartmentLevel.CITY,
                "quan9@cityfix.vn",
                "0909999999",
                true
        );

        mockResponse = new DepartmentResponse(
                departmentId,
                "Phòng xử lý sự cố hạ tầng phường Long Trường",
                DepartmentLevel.WARD,
                "longtruong@cityfix.vn",
                "0901234567",
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0
        );
    }

    @Test
    @DisplayName("createDepartment_validRequest_returns200OK")
    void createDepartment_validRequest_returns200OK() {
        when(departmentService.createDepartment(any(CreateDepartmentRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<DepartmentResponse>> response = departmentController.createDepartment(validCreateRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().name()).isEqualTo(validCreateRequest.name());

        verify(departmentService, times(1)).createDepartment(validCreateRequest);
    }

    @Test
    @DisplayName("validation_missingName_producesConstraintViolation")
    void validation_missingName_producesConstraintViolation() {
        CreateDepartmentRequest invalidRequest = new CreateDepartmentRequest(
                "",
                DepartmentLevel.WARD,
                "longtruong@cityfix.vn",
                "0901234567"
        );

        Set<ConstraintViolation<CreateDepartmentRequest>> violations = validator.validate(invalidRequest);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Tên phòng ban không được để trống"));
    }

    @Test
    @DisplayName("getAllDepartments_validParams_returns200OKWithPage")
    void getAllDepartments_validParams_returns200OKWithPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DepartmentResponse> mockPage = new PageImpl<>(List.of(mockResponse));

        when(departmentService.getAllDepartments(pageable, false)).thenReturn(mockPage);

        ResponseEntity<BaseResponse<Page<DepartmentResponse>>> response = departmentController.getAllDepartments(false, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getDepartmentById_validId_returns200OK")
    void getDepartmentById_validId_returns200OK() {
        when(departmentService.getDepartmentById(departmentId)).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<DepartmentResponse>> response = departmentController.getDepartmentById(departmentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().departmentId()).isEqualTo(departmentId);
    }

    @Test
    @DisplayName("updateDepartment_validRequest_returns200OK")
    void updateDepartment_validRequest_returns200OK() {
        when(departmentService.updateDepartment(eq(departmentId), any(UpdateDepartmentRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<DepartmentResponse>> response = departmentController.updateDepartment(departmentId, validUpdateRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(departmentService, times(1)).updateDepartment(departmentId, validUpdateRequest);
    }

    @Test
    @DisplayName("deleteDepartment_validId_returns200OK")
    void deleteDepartment_validId_returns200OK() {
        doNothing().when(departmentService).deleteDepartment(departmentId);

        ResponseEntity<BaseResponse<Void>> response = departmentController.deleteDepartment(departmentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(departmentService, times(1)).deleteDepartment(departmentId);
    }
}
