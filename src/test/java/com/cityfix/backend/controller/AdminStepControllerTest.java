package com.cityfix.backend.controller;

import com.cityfix.backend.model.dto.request.StepRequest;
import com.cityfix.backend.model.dto.response.BaseResponse;
import com.cityfix.backend.model.dto.response.StepResponse;
import com.cityfix.backend.service.StepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminStepControllerTest {

    @Mock
    private StepService stepService;

    @InjectMocks
    private AdminStepController adminStepController;

    private UUID stepId;
    private UUID subCategoryId;
    private StepRequest validRequest;
    private StepResponse mockResponse;

    @BeforeEach
    void setUp() {
        stepId = UUID.randomUUID();
        subCategoryId = UUID.randomUUID();

        validRequest = new StepRequest();
        validRequest.setSubCategoryId(subCategoryId);
        validRequest.setStepName("Khảo sát");
        validRequest.setDescription("Khảo sát tình hình");
        validRequest.setDuration(24);

        mockResponse = StepResponse.builder()
                .stepId(stepId)
                .subCategoryId(subCategoryId)
                .stepName("Khảo sát")
                .description("Khảo sát tình hình")
                .duration(24)
                .build();
    }

    @Test
    @DisplayName("getAllSteps_returns200OK")
    void getAllSteps_returns200OK() {
        when(stepService.getAllSteps()).thenReturn(List.of(mockResponse));

        ResponseEntity<BaseResponse<List<StepResponse>>> response = adminStepController.getAllSteps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getStepsBySubCategory_returns200OK")
    void getStepsBySubCategory_returns200OK() {
        when(stepService.getStepsBySubCategoryId(subCategoryId)).thenReturn(List.of(mockResponse));

        ResponseEntity<BaseResponse<List<StepResponse>>> response = adminStepController.getStepsBySubCategory(subCategoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("createStep_validRequest_returns200OK")
    void createStep_validRequest_returns200OK() {
        when(stepService.createStep(any(StepRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<StepResponse>> response = adminStepController.createStep(validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(stepService, times(1)).createStep(validRequest);
    }

    @Test
    @DisplayName("updateStep_validRequest_returns200OK")
    void updateStep_validRequest_returns200OK() {
        when(stepService.updateStep(eq(stepId), any(StepRequest.class))).thenReturn(mockResponse);

        ResponseEntity<BaseResponse<StepResponse>> response = adminStepController.updateStep(stepId, validRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(stepService, times(1)).updateStep(stepId, validRequest);
    }

    @Test
    @DisplayName("deleteStep_validId_returns200OK")
    void deleteStep_validId_returns200OK() {
        doNothing().when(stepService).deleteStep(stepId);

        ResponseEntity<BaseResponse<Void>> response = adminStepController.deleteStep(stepId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(stepService, times(1)).deleteStep(stepId);
    }
}
