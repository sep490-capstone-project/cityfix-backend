package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.mapper.SubCategoryMapper;
import com.cityfix.backend.model.dto.request.SubCategoryRequest;
import com.cityfix.backend.model.dto.response.SubCategoryResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.model.entity.SubCategory;
import com.cityfix.backend.repository.CategoryRepository;
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
    private final SubCategoryMapper subCategoryMapper;

    @Override
    @Transactional
    public SubCategoryResponse createSubCategory(SubCategoryRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        SubCategory subCategory = subCategoryMapper.toEntity(request);
        subCategory.setCategory(category);
        
        return subCategoryMapper.toResponse(subCategoryRepository.save(subCategory));
    }

    @Override
    @Transactional
    public SubCategoryResponse updateSubCategory(UUID id, SubCategoryRequest request) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        subCategory.setCategory(category);
        subCategory.setName(request.getName());
        subCategory.setDescription(request.getDescription());
        subCategory.setSla(request.getSla());
        subCategory.setPriority(request.getPriority());
        subCategory.setActive(request.getIsActive());

        return subCategoryMapper.toResponse(subCategoryRepository.save(subCategory));
    }

    @Override
    @Transactional
    public void deleteSubCategory(UUID id) {
        SubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));
        
        subCategory.setActive(false);
        subCategory.setDeleted(true);
        subCategory.setDeletedAt(LocalDateTime.now());
        subCategoryRepository.save(subCategory);
    }

    @Override
    public SubCategoryResponse getSubCategory(UUID id) {
        return subCategoryRepository.findById(id)
                .map(subCategoryMapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.SUBCATEGORY_NOT_FOUND));
    }

    @Override
    public List<SubCategoryResponse> getAllSubCategories() {
        return subCategoryRepository.findAll().stream()
                .map(subCategoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SubCategoryResponse> getSubCategoriesByCategoryId(UUID categoryId) {
        return subCategoryRepository.findByCategory_CategoryIdAndIsActiveTrue(categoryId).stream()
                .map(subCategoryMapper::toResponse)
                .collect(Collectors.toList());
    }
}
