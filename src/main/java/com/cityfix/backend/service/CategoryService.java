package com.cityfix.backend.service;

import com.cityfix.backend.model.dto.request.CategoryRequest;
import com.cityfix.backend.model.dto.response.CategoryResponse;
import java.util.List;
import java.util.UUID;

public interface CategoryService {
    List<CategoryResponse> getAllCategories();
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(UUID id, CategoryRequest request);
    void deleteCategory(UUID id);
}
