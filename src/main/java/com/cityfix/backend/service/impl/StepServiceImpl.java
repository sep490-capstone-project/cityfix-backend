package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.StepMapper;
import com.cityfix.backend.model.dto.request.StepRequest;
import com.cityfix.backend.model.dto.response.StepResponse;
import com.cityfix.backend.model.entity.Step;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.repository.StepRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.service.StepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StepServiceImpl implements StepService {

    private final StepRepository stepRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final StepMapper stepMapper;

    @Override
    @Transactional
    public StepResponse createStep(StepRequest request) {
        SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(request.getSubCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        Step step = stepMapper.toEntity(request);
        step.setSubCategory(subCategory);

        return stepMapper.toResponse(stepRepository.save(step));
    }

    @Override
    @Transactional
    public StepResponse updateStep(UUID id, StepRequest request) {
        Step step = stepRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(request.getSubCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        step.setSubCategory(subCategory);
        step.setStepName(request.getStepName());
        step.setDescription(request.getDescription());
        step.setDuration(request.getDuration());

        return stepMapper.toResponse(stepRepository.save(step));
    }

    @Override
    @Transactional
    public void deleteStep(UUID id) {
        Step step = stepRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));
        
        stepRepository.delete(step);
    }

    @Override
    public List<StepResponse> getAllSteps() {
        return stepRepository.findAll().stream()
                .map(stepMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<StepResponse> getStepsBySubCategoryId(UUID subCategoryId) {
        return stepRepository.findBySubCategory_SubCategoryId(subCategoryId).stream()
                .map(stepMapper::toResponse)
                .collect(Collectors.toList());
    }
}
