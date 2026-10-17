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

import java.time.LocalDateTime;
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
        Step savedStep = stepRepository.save(step);

        recalculateSubCategorySla(subCategory);

        return stepMapper.toResponse(savedStep);
    }

    @Override
    @Transactional
    public StepResponse updateStep(UUID id, StepRequest request) {
        Step step = stepRepository.findByStepIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        SubCategory oldSubCategory = step.getSubCategory();

        SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(request.getSubCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        step.setSubCategory(subCategory);
        step.setStepName(request.getStepName());
        step.setDescription(request.getDescription());
        step.setDuration(request.getDuration());

        Step savedStep = stepRepository.save(step);

        if (oldSubCategory != null && oldSubCategory.getSubCategoryId() != null
                && !oldSubCategory.getSubCategoryId().equals(subCategory.getSubCategoryId())) {
            recalculateSubCategorySla(oldSubCategory);
        }
        recalculateSubCategorySla(subCategory);

        return stepMapper.toResponse(savedStep);
    }

    @Override
    @Transactional
    public void deleteStep(UUID id) {
        Step step = stepRepository.findByStepIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.STEP_NOT_FOUND));

        SubCategory subCategory = step.getSubCategory();
        step.setDeleted(true);
        step.setDeletedAt(LocalDateTime.now());
        stepRepository.save(step);

        if (subCategory != null) {
            recalculateSubCategorySla(subCategory, id);
        }
    }

    private void recalculateSubCategorySla(SubCategory subCategory) {
        recalculateSubCategorySla(subCategory, null);
    }

    private void recalculateSubCategorySla(SubCategory subCategory, UUID excludedStepId) {
        if (subCategory == null || subCategory.getSubCategoryId() == null) {
            return;
        }
        List<Step> steps = stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCategory.getSubCategoryId());
        int totalSla = 0;
        if (steps != null) {
            totalSla = steps.stream()
                    .filter(s -> s != null && !s.isDeleted())
                    .filter(s -> excludedStepId == null || !excludedStepId.equals(s.getStepId()))
                    .filter(s -> s.getDuration() != null && s.getDuration() > 0)
                    .mapToInt(Step::getDuration)
                    .sum();
        }
        subCategory.setSla(totalSla);
        subCategoryRepository.save(subCategory);
    }

    @Override
    public List<StepResponse> getAllSteps() {
        return stepRepository.findByIsDeletedFalse().stream()
                .map(stepMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<StepResponse> getStepsBySubCategoryId(UUID subCategoryId) {
        return stepRepository.findBySubCategory_SubCategoryIdAndIsDeletedFalse(subCategoryId).stream()
                .map(stepMapper::toResponse)
                .collect(Collectors.toList());
    }
}
