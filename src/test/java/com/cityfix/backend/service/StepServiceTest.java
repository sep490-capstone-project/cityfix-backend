package com.cityfix.backend.service;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.StepMapper;
import com.cityfix.backend.model.dto.request.StepRequest;
import com.cityfix.backend.model.dto.response.StepResponse;
import com.cityfix.backend.model.entity.Step;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.repository.StepRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.service.impl.StepServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepServiceTest {

    @Mock
    private StepRepository stepRepository;

    @Mock
    private SubCategoryRepository subCategoryRepository;

    @Mock
    private StepMapper stepMapper;

    @InjectMocks
    private StepServiceImpl stepService;

    private StepRequest request;
    private SubCategory subCategory;
    private Step step;
    private StepResponse response;
    private UUID stepId;
    private UUID subCategoryId;

    @BeforeEach
    void setUp() {
        stepId = UUID.randomUUID();
        subCategoryId = UUID.randomUUID();

        request = new StepRequest();
        request.setSubCategoryId(subCategoryId);
        request.setStepName("Khảo sát");
        request.setDescription("Khảo sát chi tiết");
        request.setDuration(24);

        subCategory = SubCategory.builder()
                .subCategoryId(subCategoryId)
                .name("Test SubCategory")
                .build();

        step = Step.builder()
                .stepId(stepId)
                .subCategory(subCategory)
                .stepName("Khảo sát")
                .description("Khảo sát chi tiết")
                .duration(24)
                .build();

        response = StepResponse.builder()
                .stepId(stepId)
                .subCategoryId(subCategoryId)
                .stepName("Khảo sát")
                .description("Khảo sát chi tiết")
                .duration(24)
                .build();
    }

    @Test
    void createStep_Success() {
        Step step2 = Step.builder()
                .stepId(UUID.randomUUID())
                .subCategory(subCategory)
                .stepName("Sửa chữa")
                .duration(12)
                .build();
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(request.getSubCategoryId())).thenReturn(Optional.of(subCategory));
        when(stepMapper.toEntity(request)).thenReturn(step);
        when(stepRepository.save(any(Step.class))).thenReturn(step);
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCategoryId)).thenReturn(java.util.List.of(step, step2));
        when(stepMapper.toResponse(step)).thenReturn(response);

        StepResponse result = stepService.createStep(request);

        assertNotNull(result);
        assertEquals("Khảo sát", result.getStepName());
        assertEquals(24, result.getDuration());
        assertEquals(36, subCategory.getSla());
        verify(stepRepository).save(any(Step.class));
        verify(subCategoryRepository).save(subCategory);
    }

    @Test
    void createStep_SubCategoryNotFound() {
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(request.getSubCategoryId())).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> stepService.createStep(request));
        assertEquals(ErrorCode.SUBCATEGORY_NOT_FOUND, ex.getErrorCode());
    }
    
    @Test
    void updateStep_Success() {
        request.setDuration(48);
        when(stepRepository.findByStepIdAndIsDeletedFalse(stepId)).thenReturn(Optional.of(step));
        when(subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(request.getSubCategoryId())).thenReturn(Optional.of(subCategory));
        when(stepRepository.save(any(Step.class))).thenReturn(step);
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCategoryId)).thenReturn(java.util.List.of(step));
        when(stepMapper.toResponse(step)).thenReturn(response);

        StepResponse result = stepService.updateStep(stepId, request);

        assertNotNull(result);
        assertEquals("Khảo sát", result.getStepName());
        assertEquals(48, subCategory.getSla());
        verify(stepRepository).save(any(Step.class));
        verify(subCategoryRepository).save(subCategory);
    }

    @Test
    void updateStep_StepNotFound() {
        when(stepRepository.findByStepIdAndIsDeletedFalse(stepId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> stepService.updateStep(stepId, request));
        assertEquals(ErrorCode.STEP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void deleteStep_Success() {
        Step remainingStep = Step.builder()
                .stepId(UUID.randomUUID())
                .subCategory(subCategory)
                .duration(10)
                .build();
        when(stepRepository.findByStepIdAndIsDeletedFalse(stepId)).thenReturn(Optional.of(step));
        when(stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCategoryId)).thenReturn(java.util.List.of(remainingStep));
        
        stepService.deleteStep(stepId);
        
        assertTrue(step.isDeleted());
        assertNotNull(step.getDeletedAt());
        verify(stepRepository).save(step);
        assertEquals(10, subCategory.getSla());
        verify(subCategoryRepository).save(subCategory);
    }
}
