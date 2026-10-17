package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.SubCategoryMapper;
import com.cityfix.backend.model.dto.request.SubCategoryRequest;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.Step;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.repository.StepRepository;
import com.cityfix.backend.repository.SubCategoryRepository;
import com.cityfix.backend.service.SubCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubCategoryServiceImpl implements SubCategoryService {

    private final SubCategoryRepository subCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final StepRepository stepRepository;
    private final SubCategoryMapper subCategoryMapper;

    @Override
    @Transactional
    public SubCategoryResponse createSubCategory(SubCategoryRequest request) {
        Category category = categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        SubCategory subCategory = subCategoryMapper.toEntity(request);
        subCategory.setCategory(category);
        subCategory.setSla(0);
        
        return subCategoryMapper.toResponse(subCategoryRepository.save(subCategory));
    }

    @Override
    @Transactional
    public SubCategoryResponse updateSubCategory(UUID id, SubCategoryRequest request) {
        SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        Category category = categoryRepository.findByCategoryIdAndIsDeletedFalse(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        subCategory.setCategory(category);
        subCategory.setName(request.getName());
        subCategory.setDescription(request.getDescription());
        subCategory.setPriority(request.getPriority());
        subCategory.setActive(request.getIsActive());

        List<Step> steps = stepRepository.findBySubCategory_SubCategoryId(id);
        int totalSla = (steps == null) ? 0 : steps.stream()
                .filter(s -> s != null && !s.isDeleted() && s.getDuration() != null && s.getDuration() > 0)
                .mapToInt(Step::getDuration)
                .sum();
        subCategory.setSla(totalSla);

        return subCategoryMapper.toResponse(subCategoryRepository.save(subCategory));
    }

    @Override
    @Transactional
    public void deleteSubCategory(UUID id) {
        SubCategory subCategory = subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));
        
        subCategory.setActive(false);
        subCategory.setDeleted(true);
        subCategory.setDeletedAt(LocalDateTime.now());
        subCategoryRepository.save(subCategory);
    }

    @Override
    public SubCategoryResponse getSubCategory(UUID id) {
        return subCategoryRepository.findBySubCategoryIdAndIsDeletedFalse(id)
                .map(subCategoryMapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));
    }

    @Override
    public List<SubCategoryResponse> getAllSubCategories() {
        return subCategoryRepository.findByIsDeletedFalse().stream()
                .map(subCategoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SubCategoryResponse> getSubCategoriesByCategoryId(UUID categoryId) {
        return subCategoryRepository.findByCategory_CategoryIdAndIsActiveTrueAndIsDeletedFalse(categoryId).stream()
                .map(subCategoryMapper::toResponse)
                .collect(Collectors.toList());
    }
}
