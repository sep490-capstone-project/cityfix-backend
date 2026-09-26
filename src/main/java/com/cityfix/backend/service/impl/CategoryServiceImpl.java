package com.cityfix.backend.service.impl;

import com.cityfix.backend.exception.AppException;
import com.cityfix.backend.exception.ErrorCode;
import com.cityfix.backend.model.dto.request.CategoryRequest;
import com.cityfix.backend.model.dto.response.CategoryResponse;
import com.cityfix.backend.model.entity.Category;
import com.cityfix.backend.repository.CategoryRepository;
import com.cityfix.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByIsDeletedFalse().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        categoryRepository.findByNameAndIsDeletedFalse(request.getName()).ifPresent(existing -> {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên danh mục đã tồn tại");
        });

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sla(request.getSla())
                .build();
        Category savedCategory = categoryRepository.save(category);
        return mapToResponse(savedCategory);
    }

    @Override
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findByCategoryIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        categoryRepository.findByNameAndIsDeletedFalse(request.getName()).ifPresent(existing -> {
            if (!existing.getCategoryId().equals(id)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên danh mục đã tồn tại");
            }
        });

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setSla(request.getSla());
        
        Category updatedCategory = categoryRepository.save(category);
        return mapToResponse(updatedCategory);
    }

    @Override
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findByCategoryIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        
        category.setDeleted(true);
        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .description(category.getDescription())
                .sla(category.getSla())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
